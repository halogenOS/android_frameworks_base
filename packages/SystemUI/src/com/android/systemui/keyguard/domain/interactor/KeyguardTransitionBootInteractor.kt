/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.domain.interactor

import android.util.Log
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.data.repository.KeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.KeyguardState.OFF
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.scene.domain.interactor.OnBootTransitionInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Handles initialization of the KeyguardTransitionRepository on boot. */
@SysUISingleton
class KeyguardTransitionBootInteractor
@Inject
constructor(
    @Application val scope: CoroutineScope,
    val deviceEntryInteractor: DeviceEntryInteractor,
    val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    val internalTransitionInteractor: InternalKeyguardTransitionInteractor,
    val onBootTransitionInteractor: OnBootTransitionInteractor,
    val keyguardInteractor: KeyguardInteractor,
    val repository: KeyguardTransitionRepository,
) {
    /**
     * Whether the boot transition has not started yet or is still fading in the lock screen. Until
     * [start] emits the first step, the boot target is unknown. A boot that leads to another state
     * also counts as pending until then.
     */
    val isLockscreenFadeInPending: Flow<Boolean> =
        keyguardTransitionInteractor.transitionState.map { it.isBootLockscreenFadeInPending }

    fun start() {
        scope.launch {
            if (internalTransitionInteractor.currentTransitionInfoInternal().from != OFF) {
                Log.e(
                    "KeyguardTransitionBootInteractor",
                    "We've already transitioned to a state other than OFF. We'll respect that " +
                        "transition, but this should not happen.",
                )
            } else {
                if (SceneContainerFlag.isEnabled) {
                    repository.emitInitialStepsFromOff(LOCKSCREEN)
                } else {
                    val state =
                        if (keyguardInteractor.primaryBouncerShowing.value) {
                            // SIM bouncer may show first.
                            KeyguardState.PRIMARY_BOUNCER
                        } else if (onBootTransitionInteractor.showLockscreenOnBoot()) {
                            KeyguardState.LOCKSCREEN
                        } else {
                            KeyguardState.GONE
                        }
                    repository.emitInitialStepsFromOff(state)
                }
            }
        }
    }

    /** Before [start], the step is the initial OFF to OFF step of the transition repository. */
    private val TransitionStep.isBootLockscreenFadeInPending: Boolean
        get() =
            from == OFF &&
                when (to) {
                    OFF -> true
                    LOCKSCREEN -> transitionState.isTransitioning()
                    else -> false
                }
}
