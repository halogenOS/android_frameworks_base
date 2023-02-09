package com.android.server.pm.permission;

import android.content.Context;
import android.util.ArraySet;
import android.util.EmptyArray;

import com.android.internal.pm.pkg.component.ParsedUsesPermission;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;

public class SpecialRuntimePermUtils {
    private static final String TAG = SpecialRuntimePermUtils.class.getSimpleName();

    private static final ArraySet<String> specialRuntimePermissions = new ArraySet<>(new String[] {
    });

    public static boolean isSpecialRuntimePermission(String permission) {
        return specialRuntimePermissions.contains(permission);
    }

    public static String[] getAll() {
        return specialRuntimePermissions.toArray(EmptyArray.STRING);
    }

    public static boolean shouldAutoGrant(Context ctx, String packageName, int userId, String perm) {
        if (!isSpecialRuntimePermission(perm)) {
            return false;
        }

        return true;
    }

    public static int getFlags(AndroidPackage pkg, PackageState pkgState, int userId) {
        int flags = 0;

        for (ParsedUsesPermission perm : pkg.getUsesPermissionMapping().values()) {
            String name = perm.getName();
            switch (name) {
                default:
                    continue;
            }
        }

        return flags;
    }

    private SpecialRuntimePermUtils() {}
}
