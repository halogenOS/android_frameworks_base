package android.app;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;

import com.android.internal.util.Preconditions;

import java.util.Objects;

class ActivityThreadHooks {

    @Nullable // null during the early part of app process init
    private static volatile Context appContext;
    private static volatile boolean onBindCalled;

    // called after the initial app context is constructed
    // ActivityThread.handleBindApplication
    static Bundle onBind(ActivityThread.AppBindData appBindData) {
        Bundle args = appBindData.extraArgs;
        Objects.requireNonNull(args, "args bundle is null");

        Preconditions.checkState(!onBindCalled);
        onBindCalled = true;

        int[] flags = Objects.requireNonNull(args.getIntArray(AppBindArgs.KEY_FLAGS_ARRAY));

        return args;
    }

    // called after ActivityThread instrumentation is inited, which happens before execution of any
    // of app's code
    // ActivityThread.handleBindApplication
    static void onBind2(Context appContext, Bundle appBindArgs) {
        ActivityThreadHooks.appContext = appContext;
    }

    static Service instantiateService(String className) {
        Service res = null;
        return res;
    }
}
