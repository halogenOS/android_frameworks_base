/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import android.annotation.NonNull;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Reports the boot to the boot animation: each stage as the boot reaches it, how far the app
 * update is, and the request to end the animation. When the boot completes, it saves how long
 * each stage took in persist.bootanim.stage_times, and the boot animation of the next boot sizes
 * the part of its progress bar for each stage by them. SystemServer creates the one instance and
 * publishes it in {@link LocalServices}. The boot animation reads the properties in
 * cmds/bootanimation/src/boot_report.rs, and the stage times in
 * cmds/bootanimation/src/boot_progress/stage_times.rs, which describes their format.
 */
public final class BootAnimationReporter {
    private static final String TAG = "BootAnimationReporter";
    private static final String STAGE_PROPERTY = "service.bootanim.stage";
    private static final String STEP_PROPERTY = "service.bootanim.step";
    private static final String EXIT_PROPERTY = "service.bootanim.exit";
    private static final String STAGE_TIMES_PROPERTY = "persist.bootanim.stage_times";
    /**
     * How far odrefresh has compiled the system, which it reports above 0 once it has compiled
     * anything. SurfaceFlinger sets it to 0 when it starts the boot animation.
     */
    private static final String COMPILE_PROGRESS_PROPERTY = "service.bootanim.progress";
    /** When init started the boot animation, in nanoseconds of the elapsed realtime clock. */
    private static final String ANIMATION_START_PROPERTY = "ro.boottime.bootanim";
    /** When init started odsign, which runs odrefresh, in nanoseconds of elapsed realtime. */
    private static final String ODSIGN_START_PROPERTY = "ro.boottime.odsign";
    /** When init started zygote, once odsign was done, in nanoseconds of elapsed realtime. */
    private static final String ZYGOTE_START_PROPERTY = "ro.boottime.zygote";
    /**
     * The key of the time from the start of the boot animation until the first stage, without the
     * compile.
     */
    private static final String START_KEY = "start";
    /** The key of the time in which odrefresh compiled the system while the animation ran. */
    private static final String COMPILE_KEY = "compile";
    /** The times in the property are in tenths of a second. */
    private static final double MILLIS_PER_UNIT = 100.0;

    /**
     * Whether system_server runs after a runtime restart. The boot animation also runs after a
     * restart of SurfaceFlinger, which restarts system_server too, so the stages are reported
     * then as well, but the times of a runtime restart describe no boot and are not saved.
     */
    private final boolean mRuntimeRestart;

    private final Object mLock = new Object();
    /** When the boot reached each stage, in milliseconds of elapsed realtime. */
    @GuardedBy("mLock")
    private final EnumMap<BootAnimationStage, Long> mReachedAt =
            new EnumMap<>(BootAnimationStage.class);
    /**
     * When the boot animation was asked to end, which ends the last stage, in milliseconds of
     * elapsed realtime, or null before.
     */
    @GuardedBy("mLock")
    private Long mEndedAt;

    /** A reporter for a system_server that runs after a runtime restart or not. */
    public BootAnimationReporter(boolean runtimeRestart) {
        mRuntimeRestart = runtimeRestart;
    }

    /**
     * Tells the boot animation that the boot has reached stage, and notes when for the stage
     * times. Only the first report of a stage counts for the times.
     */
    public void reach(@NonNull BootAnimationStage stage) {
        synchronized (mLock) {
            mReachedAt.putIfAbsent(stage, SystemClock.elapsedRealtime());
        }
        setProperty(STAGE_PROPERTY, stage.key());
    }

    /** Tells the boot animation how far the app update is, from 0 to 100 percent. */
    public void reportAppUpdate(int percent) {
        setProperty(STEP_PROPERTY, Integer.toString(percent));
    }

    /** Asks the boot animation to end, which ends the last stage. */
    public void requestExit() {
        SystemProperties.set(EXIT_PROPERTY, "1");
        synchronized (mLock) {
            if (mEndedAt == null) {
                mEndedAt = SystemClock.elapsedRealtime();
            }
        }
    }

    /**
     * Saves the times of this boot for the boot animation of the next boot. A runtime restart
     * saves nothing.
     */
    public void save() {
        if (mRuntimeRestart) {
            return;
        }
        final Map<BootAnimationStage, Long> reachedAt;
        final Long endedAt;
        synchronized (mLock) {
            reachedAt = new EnumMap<>(mReachedAt);
            endedAt = mEndedAt;
        }
        if (endedAt == null) {
            Slog.w(TAG, "The boot animation was not asked to end, so the last stage has no end");
            return;
        }
        setProperty(STAGE_TIMES_PROPERTY, format(reachedAt, endedAt));
    }

    /**
     * Sets the property name to value, and logs a failure instead of throwing it: a boot
     * animation without this report is no reason to stop the boot.
     */
    private static void setProperty(@NonNull String name, @NonNull String value) {
        try {
            SystemProperties.set(name, value);
        } catch (RuntimeException e) {
            Slog.w(TAG, "Could not set " + name + " to " + value, e);
        }
    }

    /**
     * The times of a boot in the format of the property, with the stages reached at reachedAt and
     * the boot animation asked to end at endedAt. Each stage lasts until the next one is reached,
     * the last one until endedAt.
     */
    @NonNull
    private static String format(@NonNull Map<BootAnimationStage, Long> reachedAt, long endedAt) {
        final List<BootAnimationStage> stages = new ArrayList<>(reachedAt.keySet());
        stages.sort(Comparator.comparingLong(reachedAt::get));
        final StringJoiner times = new StringJoiner(",");
        final long firstReachedAt = stages.isEmpty() ? endedAt : reachedAt.get(stages.get(0));
        // An animation that starts after the first stage shows none of the time before it.
        readBootTime(ANIMATION_START_PROPERTY).ifPresent(animationStart -> {
            final OptionalLong compile = readCompileTime(animationStart);
            times.add(formatEntry(START_KEY,
                    Math.max(0, firstReachedAt - animationStart - compile.orElse(0))));
            compile.ifPresent(millis -> times.add(formatEntry(COMPILE_KEY, millis)));
        });
        for (int i = 0; i < stages.size(); i++) {
            final long until = i + 1 < stages.size() ? reachedAt.get(stages.get(i + 1)) : endedAt;
            times.add(formatEntry(stages.get(i).key(), until - reachedAt.get(stages.get(i))));
        }
        return times.toString();
    }

    @NonNull
    private static String formatEntry(@NonNull String key, long millis) {
        return key + "=" + Math.round(millis / MILLIS_PER_UNIT);
    }

    /**
     * How long odrefresh compiled the system while the boot animation ran, which init started at
     * animationStart, in milliseconds, or empty when it did not compile then. odrefresh runs
     * inside odsign, before init starts zygote.
     */
    @NonNull
    private static OptionalLong readCompileTime(long animationStart) {
        // The progress is above 0 only when odrefresh compiled after the animation started.
        if (SystemProperties.getInt(COMPILE_PROGRESS_PROPERTY, 0) <= 0) {
            return OptionalLong.empty();
        }
        final OptionalLong odsignStart = readBootTime(ODSIGN_START_PROPERTY);
        final OptionalLong zygoteStart = readBootTime(ZYGOTE_START_PROPERTY);
        if (odsignStart.isEmpty() || zygoteStart.isEmpty()) {
            return OptionalLong.empty();
        }
        final long compileStart = Math.max(odsignStart.getAsLong(), animationStart);
        return OptionalLong.of(Math.max(0, zygoteStart.getAsLong() - compileStart));
    }

    /**
     * When init started a service, as recorded in property, in milliseconds of elapsed realtime,
     * or empty when it did not start it on this boot or the start is not a number, which is
     * logged.
     */
    @NonNull
    private static OptionalLong readBootTime(@NonNull String property) {
        final String start = SystemProperties.get(property);
        if (start.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(TimeUnit.NANOSECONDS.toMillis(Long.parseLong(start)));
        } catch (NumberFormatException e) {
            Slog.w(TAG, property + " is " + start + ", which is not a number", e);
            return OptionalLong.empty();
        }
    }
}
