/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2026 The halogenOS Project
 */

package com.android.server.display.mode;

import android.annotation.Nullable;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.view.Display;

import com.android.internal.os.BackgroundThread;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Set;

/**
 * Holds the built-in display at its highest refresh rate while an LTPO mode is active.
 *
 * LTPO panels reach their lower rates by skipping frames of their highest display mode, which
 * the LTPO HAL drives on its own; any other display mode leaves the panel unable to do so. The
 * mode is chosen through a system property, so the vote follows that property wherever it was
 * set from, including its build default.
 */
final class LtpoObserver {
    private static final String LTPO_SERVICE =
            "custom.hardware.display.ltpo.ILtpoControl/default";
    private static final String PROP_LTPO_MODE = "persist.sys.sfm.mode";
    private static final Set<String> ACTIVE_MODES = Set.of("idle", "vrr");

    private final VotesStorage mVotesStorage;
    private final DisplayModeDirector.Injector mInjector;
    private final Handler mHandler = BackgroundThread.getHandler();

    LtpoObserver(VotesStorage votesStorage, DisplayModeDirector.Injector injector) {
        mVotesStorage = votesStorage;
        mInjector = injector;
    }

    void observe() {
        if (!ServiceManager.isDeclared(LTPO_SERVICE)) {
            return;
        }
        SystemProperties.addChangeCallback(() -> mHandler.post(this::updateVote));
        mHandler.post(this::updateVote);
    }

    private void updateVote() {
        mVotesStorage.updateVote(Display.DEFAULT_DISPLAY, Vote.PRIORITY_LTPO_REFRESH_RATE,
                currentVote());
    }

    @Nullable
    private Vote currentVote() {
        if (!ACTIVE_MODES.contains(SystemProperties.get(PROP_LTPO_MODE))) {
            return null;
        }
        final Display display = mInjector.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return null;
        }
        final double highest = Arrays.stream(display.getSupportedModes())
                .mapToDouble(Display.Mode::getRefreshRate)
                .max()
                .orElse(0);
        return highest > 0 ? Vote.forPhysicalRefreshRates((float) highest, (float) highest)
                : null;
    }

    void dump(PrintWriter pw) {
        pw.println("  LtpoObserver");
        pw.println("    " + PROP_LTPO_MODE + "=" + SystemProperties.get(PROP_LTPO_MODE));
    }
}
