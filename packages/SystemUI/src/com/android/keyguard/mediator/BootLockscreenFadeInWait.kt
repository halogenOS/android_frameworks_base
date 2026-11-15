/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.keyguard.mediator

import android.annotation.BinderThread
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionBootInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first

/**
 * Delays the drawn report of a screen that turns on until the lock screen has faded in after boot.
 * Reports at once, on the calling thread, after a boot transition that leads to another state has
 * started, and after the boot transition is over.
 */
@SysUISingleton
class BootLockscreenFadeInWait
@Inject
constructor(
    @Background private val scope: CoroutineScope,
    private val keyguardTransitionBootInteractor: KeyguardTransitionBootInteractor,
) {

    @BinderThread
    fun onScreenTurningOn(onDrawn: Runnable) {
        scope.launch("BootLockscreenFadeInWait", start = CoroutineStart.UNDISPATCHED) {
            keyguardTransitionBootInteractor.isLockscreenFadeInPending.first { !it }
            onDrawn.run()
        }
    }
}
