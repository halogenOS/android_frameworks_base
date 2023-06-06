package com.android.internal.gmscompat.fileservice;

import android.annotation.Nullable;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.ext.PackageId;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.gmscompat.GmsCompatApp;

import java.io.File;
import java.io.FileDescriptor;
import java.util.ArrayList;

import libcore.io.IoBridge;
import libcore.io.IoUtils;

import static android.system.OsConstants.O_ACCMODE;
import static android.system.OsConstants.O_RDONLY;

public class GmsCoreFileServerClientHooks {
    private static final String TAG = "GmsCoreFileServerClientHooks";
    private static final boolean DEBUG = false;

    private static boolean isEnabled;
    private static String gmsCoreDeDataPrefix;
    private static String gmsCoreCeDataPrefix;
    private static volatile IFileProxyService fileProxyService;
    private static ArrayMap<String, ParcelFileDescriptor> pfdCache;
    private static ArrayList<ParcelFileDescriptor> pastCachedFds;

    public static boolean isEnabled() {
        return isEnabled;
    }

    public static void init() {
        Context context = GmsCompat.appContext();
        ApplicationInfo gmsCoreAppInfo;
        try {
            gmsCoreAppInfo = context.getPackageManager().getApplicationInfoAsUser(
                    PackageId.GMS_CORE_NAME, 0, context.getUserId());
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
        gmsCoreDeDataPrefix = gmsCoreAppInfo.deviceProtectedDataDir + "/";
        gmsCoreCeDataPrefix = gmsCoreAppInfo.credentialProtectedDataDir + "/";
        pfdCache = new ArrayMap<>(20);
        pastCachedFds = new ArrayList<>();

        File.lastModifiedHook = GmsCoreFileServerClientHooks::getFileLastModified;
        IoBridge.openFdInterceptor = new IoBridge.OpenFdInterceptor() {
            @Override
            public FileDescriptor maybeInterceptOpenFd(String path, int flags) {
                if (isInGmsCoreDataDir(path) && shouldProxyOpen(flags)) {
                    return openFreshFileDescriptor(path);
                }
                return null;
            }
        };
        isEnabled = true;
    }

    public static long getFileLastModified(File file) {
        final String path = file.getPath();

        if (isInGmsCoreDataDir(path)) {
            if (shouldCacheForLastModified(path)) {
                FileDescriptor fd = openCachedFile(path);
                return fd != null ? getFileLastModified(fd) : 0L;
            }

            ParcelFileDescriptor pfd = openFreshParcelFileDescriptor(path);
            if (pfd != null) {
                try {
                    return getFileLastModified(pfd.getFileDescriptor());
                } finally {
                    IoUtils.closeQuietly(pfd);
                }
            }
        }
        return 0L;
    }

    private static long getFileLastModified(FileDescriptor fd) {
        return new File("/proc/self/fd/" + fd.getInt$()).lastModified();
    }

    public static boolean isInGmsCoreDeDataDirAndShouldCacheFds(String path) {
        return path.startsWith(gmsCoreDeDataPrefix);
    }

    private static boolean shouldCacheForLastModified(String path) {
        // Use cache for Dynamite modules which are served as .apk files
        return isInGmsCoreDeDataDirAndShouldCacheFds(path) && isApkContainerPath(path);
    }

    private static boolean isApkContainerPath(String path) {
        return path.endsWith(".apk");
    }

    public static boolean isInGmsCoreDataDir(String path) {
        return path.startsWith(gmsCoreDeDataPrefix) || path.startsWith(gmsCoreCeDataPrefix);
    }

    public static FileDescriptor openParcelFileDescriptorHook(String path, int flags) {
        if (!isInGmsCoreDataDir(path) || !shouldProxyOpen(flags)) {
            return null;
        }

        return openFreshFileDescriptor(path);
    }

    // Returned file descriptor should never be closed because it may be accessed at any time by the native code
    @Nullable
    public static FileDescriptor openCachedFile(String path) {
        if (DEBUG) {
            Log.d(TAG, "cached path " + path, new Throwable());
        }
        try {
            ArrayMap<String, ParcelFileDescriptor> cache = pfdCache;
            // this lock isn't contended, favor simplicity, not making the critical section shorter
            synchronized (cache) {
                ParcelFileDescriptor pfd = cache.get(path);
                if (pfd == null) {
                    pfd = getFileProxyService().openFile(path);
                    if (pfd == null) {
                        return null;
                    }
                    // ParcelFileDescriptor owns the underlying file descriptor
                    cache.put(path, pfd);
                }
                return pfd.getFileDescriptor();
            }
        } catch (RemoteException e) {
            // FileProxyService never forwards exceptions to minimize the information leaks,
            // this is a very rare "binder died" exception
            throw e.rethrowAsRuntimeException();
        }
    }

    private static boolean shouldProxyOpen(int flags) {
        return (flags & O_ACCMODE) == O_RDONLY;
    }

    @Nullable
    private static FileDescriptor openFreshFileDescriptor(String path) {
        ParcelFileDescriptor pfd = openFreshParcelFileDescriptor(path);
        if (pfd == null) {
            return null;
        }

        FileDescriptor fd = new FileDescriptor();
        fd.setInt$(pfd.detachFd());
        return fd;
    }

    @Nullable
    private static ParcelFileDescriptor openFreshParcelFileDescriptor(String path) {
        if (DEBUG) {
            Log.d(TAG, "fresh path " + path, new Throwable());
        }
        try {
            IFileProxyService service;
            synchronized (pfdCache) {
                service = getFileProxyService();
            }
            return service.openFile(path);
        } catch (RemoteException e) {
            // FileProxyService never forwards exceptions to minimize the information leaks,
            // this is a very rare "binder died" exception
            throw e.rethrowAsRuntimeException();
        }
    }

    @GuardedBy("pfdCache")
    private static IFileProxyService getFileProxyService() {
        IFileProxyService cache = fileProxyService;
        if (cache != null) {
            return cache;
        }
        try {
            IFileProxyService service = GmsCompatApp.iClientOfGmsCore2Gca().getGmsCoreFileProxyService();
            fileProxyService = service;
            IBinder.DeathRecipient serviceDeathCallback = () -> {
                fileProxyService = null;
                Log.d(TAG, "FileProxyService died");
                synchronized (pfdCache) {
                    // It's not safe to close cached file descriptors, they might still be in use
                    // at this point. Simply clearing the cache would make cached ParcelFileDescriptors
                    // collectable by GC, which would close the underlying file descriptors via
                    // ParcelFileDescriptor#finalize()
                    //
                    // pastCachedFds list is effectively a file descriptor leak, but it's small and
                    // rare. File descriptor count limit (RLIMIT_NOFILE) is set to 32768 as of Android 15.
                    pastCachedFds.addAll(pfdCache.values());
                    pfdCache.clear();
                }
            };
            service.asBinder().linkToDeath(serviceDeathCallback, 0);
            return service;
        } catch (RemoteException e) {
            Log.e(TAG, "unable to obtain FileProxyService", e);
            throw e.rethrowAsRuntimeException();
        }
    }
}
