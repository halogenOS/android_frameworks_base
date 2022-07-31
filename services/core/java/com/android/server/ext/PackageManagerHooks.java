package com.android.server.ext;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppBindArgs;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;

import com.android.server.pm.Computer;
import com.android.server.pm.GosPackageStatePmHooks;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageStateInternal;

import static java.util.Objects.requireNonNull;

public class PackageManagerHooks {

    // Called when package enabled setting for a system package is deserialized from storage
    @Nullable
    public static Integer maybeOverrideSystemPackageEnabledSetting(String pkgName, @UserIdInt int userId) {
        switch (pkgName) {
            default:
                return null;
        }
    }

    public static boolean shouldBlockGrantRuntimePermission(
            PackageManagerInternal pm, String permName, String packageName, int userId)
    {
        return false;
    }

    @Nullable
    public static Bundle getExtraAppBindArgs(Context context, PackageManagerInternal pm,
                                             String packageName, int appUid, int pid) {
        if (android.os.Flags.isDevBuild()) {
            Slog.d("AppBindArgs", "obtaining args for pkgName " + packageName
                    + ", appUid " + appUid + ", pid " + pid);
        }

        // Note that:
        // - app UID differs from process UID for isolated processes
        // - for android:externalService processes (e.g. WebView processes), app UID and package
        // name values are client's, not host's

        final int appId = UserHandle.getAppId(appUid);
        final int userId = UserHandle.getUserId(appUid);

        Computer pmComputer = (Computer) pm.snapshot();

        PackageStateInternal pkgState = pmComputer.getPackageStateInternal(packageName);
        if (pkgState == null) {
            return null;
        }

        if (pkgState.getAppId() != appId) {
            return null;
        }

        AndroidPackage pkg = pkgState.getPkg();

        if (pkg == null) {
            return null;
        }

        // isSystem() remains true even if isUpdatedSystemApp() is true
        final boolean isUserApp = !pkgState.isSystem();

        GosPackageState unfilteredGosPs = pkgState.getUserStateOrDefault(userId).getGosPackageState();
        // GosPackageState that is filtered for the target app
        GosPackageState gosPs = GosPackageStatePmHooks.getFiltered(pmComputer, pkgState, unfilteredGosPs,
                appUid, pid, userId);

        ApplicationInfo appInfo =
                requireNonNull(pmComputer.getApplicationInfo(packageName, 0L, userId));

        int[] flagsArr = new int[AppBindArgs.FLAGS_ARRAY_LEN];

        var b = new Bundle();
        b.putParcelable(AppBindArgs.KEY_GOS_PACKAGE_STATE, gosPs);
        b.putIntArray(AppBindArgs.KEY_FLAGS_ARRAY, flagsArr);

        return b;
    }

    // Called when AppsFilter decides whether to restrict package visibility
    public static boolean shouldFilterApplication(
            @Nullable PackageStateInternal callingPkgSetting,
            ArraySet<PackageStateInternal> callingSharedPkgSettings,
            int callingUserId,
            PackageStateInternal targetPkgSetting, int targetUserId
    ) {
        if (callingPkgSetting != null && restrictedVisibilityPackages.contains(callingPkgSetting.getPackageName())) {
            if (!targetPkgSetting.isSystem()) {
                return true;
            }
        }

        if (restrictedVisibilityPackages.contains(targetPkgSetting.getPackageName())) {
            if (callingPkgSetting != null) {
                return !callingPkgSetting.isSystem();
            } else {
                for (int i = callingSharedPkgSettings.size() - 1; i >= 0; i--) {
                    if (!callingSharedPkgSettings.valueAt(i).isSystem()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Packages in this array are restricted from interacting with and being interacted by non-system apps
    private static final ArraySet<String> restrictedVisibilityPackages = new ArraySet<>(new String[] {
    });
}
