/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import android.annotation.NonNull;

/**
 * A stage of the boot that the boot animation shows under its progress bar, in the order of the
 * boot. {@link BootAnimationReporter} reports each stage as the boot reaches it. The boot
 * animation reads the keys in cmds/bootanimation/src/boot_stage.rs.
 */
public enum BootAnimationStage {
    /** system_server starts. */
    SYSTEM_SERVER("system_server"),
    /** The package manager scans the installed apps. */
    PACKAGES("packages"),
    /** The apps are updated after the first boot or a system update. */
    APP_UPDATE("app_update"),
    /** The remaining system services start and get ready. */
    SERVICES("services"),
    /** The system interface loads until home is drawn. */
    INTERFACE("interface");

    /** The value of service.bootanim.stage for this stage. */
    @NonNull
    private final String mKey;

    BootAnimationStage(@NonNull String key) {
        mKey = key;
    }

    /** The value of service.bootanim.stage for this stage. */
    @NonNull
    String key() {
        return mKey;
    }
}
