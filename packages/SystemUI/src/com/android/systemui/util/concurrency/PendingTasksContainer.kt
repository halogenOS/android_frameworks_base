/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.util.concurrency

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Allows to wait for multiple callbacks and notify when the last one is executed
 */
class PendingTasksContainer {

    /** The tasks registered since the last [reset] and the callback that waits for them. */
    private class Round {
        val pendingTasksCount = AtomicInteger(0)
        val completionCallback = AtomicReference<Runnable?>()

        fun complete() {
            completionCallback.getAndSet(null)?.run()
        }
    }

    @Volatile
    private var round = Round()

    /**
     * Registers a task that we should wait for
     * @return a runnable that should be invoked when the task is finished
     */
    fun registerTask(name: String): Runnable {
        val taskRound = round
        taskRound.pendingTasksCount.incrementAndGet()
        Trace.beginAsyncSection("PendingTasksContainer#$name", 0)

        return Runnable {
            Trace.endAsyncSection("PendingTasksContainer#$name", 0)
            if (taskRound.pendingTasksCount.decrementAndGet() == 0) {
                taskRound.complete()
            }
        }
    }

    /**
     * Clears state and initializes the container. Tasks of earlier rounds that finish later neither
     * count down this round nor run its callback.
     */
    fun reset() {
        round.completionCallback.set(null)
        round = Round()
    }

    /**
     * Starts waiting for all tasks to be completed
     * When all registered tasks complete it will invoke the [onComplete] callback
     */
    fun onTasksComplete(onComplete: Runnable) {
        val currentRound = round
        currentRound.completionCallback.set(onComplete)

        if (currentRound.pendingTasksCount.get() == 0) {
            currentRound.complete()
        }
    }

    /**
     * Returns current pending tasks count
     */
    fun getPendingCount(): Int = round.pendingTasksCount.get()
}
