/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.graphics.fonts;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManagerInternal;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontFileUtil;
import android.graphics.fonts.FontManager;
import android.graphics.fonts.FontUpdateRequest;
import android.graphics.fonts.FontStyle;
import android.graphics.fonts.SystemFonts;
import android.os.Build;
import android.os.ServiceSpecificException;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.SharedMemory;
import android.os.ShellCallback;
import android.system.ErrnoException;
import android.text.FontConfig;
import android.text.TextUtils;
import android.util.AndroidException;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.graphics.fonts.IFontManager;
import com.android.internal.os.BackgroundThread;
import com.android.internal.security.VerityUtils;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.Preconditions;
import com.android.server.LocalServices;
import com.android.server.SystemServerInitThreadPool;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.SystemService;
import com.android.text.flags.Flags;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.DirectByteBuffer;
import java.nio.NioUtils;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** A service for managing system fonts. */
public final class FontManagerService extends IFontManager.Stub {
    private static final String TAG = "FontManagerService";

    private static final String FONT_FILES_DIR = "/data/fonts/files";
    private static final String CONFIG_XML_FILE = "/data/fonts/config/config.xml";

    /** The font packages on the system image, found at boot. */
    @NonNull
    private volatile FontPackages mFontPackages = FontPackages.EMPTY;

    /** The value of config_defaultFontPackage, or {@code null} when it is empty. */
    @Nullable
    private final String mDefaultFontPackage;

    @android.annotation.EnforcePermission(android.Manifest.permission.UPDATE_FONTS)
    @RequiresPermission(Manifest.permission.UPDATE_FONTS)
    @Override
    public FontConfig getFontConfig() {
        super.getFontConfig_enforcePermission();

        return getSystemFontConfig();
    }

    @RequiresPermission(Manifest.permission.UPDATE_FONTS)
    @Override
    public int updateFontFamily(@NonNull List<FontUpdateRequest> requests, int baseVersion) {
        try {
            Preconditions.checkArgumentNonnegative(baseVersion);
            Objects.requireNonNull(requests);
            getContext().enforceCallingPermission(Manifest.permission.UPDATE_FONTS,
                    "UPDATE_FONTS permission required.");
            try {
                update(baseVersion, requests);
                return FontManager.RESULT_SUCCESS;
            } catch (SystemFontException e) {
                Slog.e(TAG, "Failed to update font family", e);
                return e.getErrorCode();
            }
        } finally {
            closeFileDescriptors(requests);
        }
    }

    @RequiresPermission(Manifest.permission.UPDATE_FONTS)
    @Override
    public int updateFontFallbacks(@NonNull List<FontUpdateRequest> fallbackRequests) {
        try {
            Objects.requireNonNull(fallbackRequests);
            getContext().enforceCallingPermission(Manifest.permission.UPDATE_FONTS,
                    "UPDATE_FONTS permission required.");
            try {
                updateFallbacks(fallbackRequests);
                return FontManager.RESULT_SUCCESS;
            } catch (SystemFontException e) {
                Slog.e(TAG, "Failed to update font fallbacks", e);
                return e.getErrorCode();
            }
        } finally {
            closeFileDescriptors(fallbackRequests);
        }
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public int installCustomFontFile(@NonNull ParcelFileDescriptor fd) {
        super.installCustomFontFile_enforcePermission();
        try {
            synchronized (mUpdatableFontDirLock) {
                if (mUpdatableFontDir == null) {
                    return FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED;
                }
                mUpdatableFontDir.installCustomFontFile(fd.getFileDescriptor());
                updateSerializedFontMap();
                mUserFontOverlays.applyFontForStartedUsers();
                return FontManager.RESULT_SUCCESS;
            }
        } catch (SystemFontException e) {
            Slog.e(TAG, "Failed to install custom font file", e);
            return e.getErrorCode();
        } finally {
            try {
                fd.close();
            } catch (IOException e) {
                Slog.w(TAG, "Failed to close fd", e);
            }
        }
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public int installCustomFontFamily(@NonNull List<FontUpdateRequest> familyRequests) {
        super.installCustomFontFamily_enforcePermission();
        try {
            synchronized (mUpdatableFontDirLock) {
                if (mUpdatableFontDir == null) {
                    return FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED;
                }
                mUpdatableFontDir.updateCustomFonts(familyRequests);
                updateSerializedFontMap();
                mUserFontOverlays.applyFontForStartedUsers();
                return FontManager.RESULT_SUCCESS;
            }
        } catch (SystemFontException e) {
            Slog.e(TAG, "Failed to install custom font family", e);
            return e.getErrorCode();
        } finally {
            closeFileDescriptors(familyRequests);
        }
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public int removeCustomFontFamily(@NonNull String familyName) {
        super.removeCustomFontFamily_enforcePermission();
        if (mFontPackages.contains(familyName)) {
            Slog.w(TAG, "Cannot remove a font family that comes from a font package: "
                    + familyName);
            return FontManager.RESULT_ERROR_INVALID_FONT_NAME;
        }
        try {
            synchronized (mUpdatableFontDirLock) {
                if (mUpdatableFontDir == null) {
                    return FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED;
                }
                mUpdatableFontDir.removeCustomFontFamily(familyName);
                updateSerializedFontMap();
                mUserFontOverlays.applyFontForStartedUsers();
            }
        } catch (SystemFontException e) {
            Slog.e(TAG, "Failed to remove custom font family", e);
            return e.getErrorCode();
        }
        return FontManager.RESULT_SUCCESS;
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public @NonNull List<String> getCustomFontFamilyNames() {
        super.getCustomFontFamilyNames_enforcePermission();
        synchronized (mUpdatableFontDirLock) {
            return new ArrayList<>(getInstalledFontFamilyNames());
        }
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public int setActiveCustomFontFamily(@Nullable String familyName) {
        super.setActiveCustomFontFamily_enforcePermission();
        if (familyName != null && !isFontFamilyInstalled(familyName)) {
            Slog.e(TAG, "Custom font family not installed: " + familyName);
            return FontManager.RESULT_ERROR_FONT_NOT_FOUND;
        }
        final int userId = UserHandle.getCallingUserId();
        if (!mUserFontOverlays.savePick(userId, familyName)) {
            Slog.e(TAG, "Failed to save the font family picked by user " + userId);
            return FontManager.RESULT_ERROR_FAILED_UPDATE_CONFIG;
        }
        return FontManager.RESULT_SUCCESS;
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public @Nullable String getActiveCustomFontFamily() {
        super.getActiveCustomFontFamily_enforcePermission();
        return mUserFontOverlays.getInstalledPick(UserHandle.getCallingUserId());
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public @Nullable String getDefaultFontFamily() {
        super.getDefaultFontFamily_enforcePermission();
        final String defaultFamily = getDefaultFontFamilyName();
        if (defaultFamily != null) {
            return defaultFamily;
        }
        String bodyFamily = mContext.getResources().getString(
                com.android.internal.R.string.config_bodyFontFamily);
        if (bodyFamily != null && !bodyFamily.isEmpty()) {
            return bodyFamily;
        }
        return null;
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public @NonNull String installCustomFontFamilyFromFile(@NonNull ParcelFileDescriptor fd) {
        super.installCustomFontFamilyFromFile_enforcePermission();
        final List<String> families =
                installCustomFontFamiliesFromFiles(Collections.singletonList(fd));
        if (families.isEmpty()) {
            throw new ServiceSpecificException(
                    FontManager.RESULT_ERROR_INVALID_FONT_FILE,
                    "Failed to install font file");
        }
        // A single file resolves to exactly one family.
        return families.get(0);
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public @NonNull List<String> installCustomFontFamilyFromFiles(
            @NonNull List<ParcelFileDescriptor> fds) {
        super.installCustomFontFamilyFromFiles_enforcePermission();
        return installCustomFontFamiliesFromFiles(fds);
    }

    /**
     * Installs one or more font files, clustering them into font families by typographic family
     * name and augmenting any already-installed families that share a name.
     *
     * <p>Each file is written to {@code /data/fonts} individually. Files are then grouped by their
     * embedded family name (e.g. all "Lato" variants together). Within a family, a variant is keyed
     * by its {@link FontStyle} (weight + slant); a newly installed variant replaces any existing
     * one with the same style, and otherwise extends the family. This makes installing variants
     * one-at-a-time and all-at-once converge to the same result.
     *
     * <p>Installation is partial-success tolerant: a file that fails to install or parse is skipped
     * and logged rather than aborting the whole batch.
     *
     * @return the names of the families that were created or augmented, in stable order.
     */
    private @NonNull List<String> installCustomFontFamiliesFromFiles(
            @NonNull List<ParcelFileDescriptor> fds) {
        try {
            synchronized (mUpdatableFontDirLock) {
                if (mUpdatableFontDir == null) {
                    throw new ServiceSpecificException(
                            FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED,
                            "Font updater disabled");
                }

                // Install each file and collect its parsed descriptor. Preserve insertion order so
                // families and their variants appear deterministically.
                final List<InstalledFont> installed = new ArrayList<>(fds.size());
                for (ParcelFileDescriptor fd : fds) {
                    try {
                        installed.add(installAndParse(fd.getFileDescriptor()));
                    } catch (SystemFontException e) {
                        Slog.e(TAG, "Skipping font file that failed to install", e);
                    }
                }
                if (installed.isEmpty()) {
                    return Collections.emptyList();
                }

                // Cluster by family name, preserving first-seen order of families.
                final Map<String, List<InstalledFont>> clusters = new LinkedHashMap<>();
                for (InstalledFont font : installed) {
                    clusters.computeIfAbsent(font.familyName, k -> new ArrayList<>()).add(font);
                }

                final List<FontUpdateRequest> requests = new ArrayList<>();
                for (Map.Entry<String, List<InstalledFont>> entry : clusters.entrySet()) {
                    requests.add(new FontUpdateRequest(
                            buildMergedFamily(entry.getKey(), entry.getValue())));
                }

                try {
                    mUpdatableFontDir.updateCustomFonts(requests);
                } catch (SystemFontException e) {
                    // The family registration failed as a unit; drop the orphaned font files so a
                    // retry starts clean.
                    for (InstalledFont font : installed) {
                        mUpdatableFontDir.removeCustomFontFileByPsName(font.psName);
                    }
                    throw e;
                }
                updateSerializedFontMap();
                mUserFontOverlays.applyFontForStartedUsers();
                return new ArrayList<>(clusters.keySet());
            }
        } catch (SystemFontException e) {
            Slog.e(TAG, "Failed to install custom font families from files", e);
            throw new ServiceSpecificException(e.getErrorCode(), e.getMessage());
        } finally {
            for (ParcelFileDescriptor fd : fds) {
                try {
                    fd.close();
                } catch (IOException e) {
                    Slog.w(TAG, "Failed to close fd", e);
                }
            }
        }
    }

    /** A font file that has been written to /data/fonts, with its parsed identity and style. */
    private static final class InstalledFont {
        final String psName;
        final String familyName;
        final FontStyle style;

        InstalledFont(String psName, String familyName, FontStyle style) {
            this.psName = psName;
            this.familyName = familyName;
            this.style = style;
        }
    }

    /**
     * Installs a single font file and parses the metadata needed to cluster it: PostScript name,
     * typographic family name, and {@link FontStyle}. Static fonts are supported — the style is
     * read from the OS/2 table; variable fonts report their default instance's weight/slant.
     */
    @GuardedBy("mUpdatableFontDirLock")
    private InstalledFont installAndParse(FileDescriptor fd) throws SystemFontException {
        final String psName = mUpdatableFontDir.installCustomFontFile(fd);
        final File fontFile = mUpdatableFontDir.getPostScriptMap().get(psName);
        if (fontFile == null) {
            throw new SystemFontException(
                    FontManager.RESULT_ERROR_FONT_NOT_FOUND,
                    "Installed font file not found for: " + psName);
        }
        try (FileInputStream fis = new FileInputStream(fontFile);
                FileChannel channel = fis.getChannel()) {
            final ByteBuffer buffer =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

            String familyName = FontFileUtil.getFamilyName(buffer, 0);
            if (familyName == null || familyName.isEmpty()) {
                // Fall back to the PostScript name so the font still installs as its own family.
                familyName = psName;
            }

            return new InstalledFont(psName, familyName, FontFileUtil.readFontStyle(buffer));
        } catch (IOException e) {
            mUpdatableFontDir.removeCustomFontFileByPsName(psName);
            throw new SystemFontException(
                    FontManager.RESULT_ERROR_INVALID_FONT_FILE,
                    "Failed to parse font metadata", e);
        }
    }

    /**
     * Builds a {@link FontUpdateRequest.Family} for {@code familyName} combining the existing
     * installed variants (if any) with {@code newFonts}. Variants are keyed by {@link FontStyle};
     * a new variant replaces a same-styled existing one, otherwise it is added.
     */
    @GuardedBy("mUpdatableFontDirLock")
    private FontUpdateRequest.Family buildMergedFamily(
            String familyName, List<InstalledFont> newFonts) {
        // Keyed by packed weight|slant so a re-installed style overwrites the previous file.
        final Map<Integer, FontUpdateRequest.Font> byStyle = new LinkedHashMap<>();

        final FontUpdateRequest.Family existing =
                mUpdatableFontDir.getCustomFontFamily(familyName);
        if (existing != null) {
            for (FontUpdateRequest.Font font : existing.getFonts()) {
                byStyle.put(styleKey(font.getFontStyle()), font);
            }
        }
        for (InstalledFont font : newFonts) {
            final FontUpdateRequest.Font f = new FontUpdateRequest.Font(
                    font.psName, font.style, 0 /* index */, "" /* fontVariationSettings */);
            byStyle.put(styleKey(font.style), f);
        }
        return new FontUpdateRequest.Family(
                familyName, new ArrayList<>(byStyle.values()));
    }

    private static int styleKey(FontStyle style) {
        return style.getWeight() | (style.getSlant() << 16);
    }

    /** The custom font families, followed by the font families of the font packages. */
    @GuardedBy("mUpdatableFontDirLock")
    private @NonNull Set<String> getInstalledFontFamilyNames() {
        final Set<String> names = new LinkedHashSet<>();
        if (mUpdatableFontDir != null) {
            names.addAll(mUpdatableFontDir.getCustomFontFamilyNames());
        }
        names.addAll(mFontPackages.getFamilyNames());
        return names;
    }

    private boolean isFontFamilyInstalled(@NonNull String familyName) {
        synchronized (mUpdatableFontDirLock) {
            return getInstalledFontFamilyNames().contains(familyName);
        }
    }

    /** The font family of the default font package, or {@code null} for the stock font. */
    private @Nullable String getDefaultFontFamilyName() {
        return mDefaultFontPackage != null && mFontPackages.contains(mDefaultFontPackage)
                ? mDefaultFontPackage : null;
    }

    private static void closeFileDescriptors(@Nullable List<FontUpdateRequest> requests) {
        // Make sure we close every passed FD, even if 'requests' is constructed incorrectly and
        // some fields are null.
        if (requests == null) return;
        for (FontUpdateRequest request : requests) {
            if (request == null) continue;
            ParcelFileDescriptor fd = request.getFd();
            if (fd == null) continue;
            try {
                fd.close();
            } catch (IOException e) {
                Slog.w(TAG, "Failed to close fd", e);
            }
        }
    }

    /* package */ static class SystemFontException extends AndroidException {
        private final int mErrorCode;

        SystemFontException(@FontManager.ResultCode int errorCode, String msg, Throwable cause) {
            super(msg, cause);
            mErrorCode = errorCode;
        }

        SystemFontException(int errorCode, String msg) {
            super(msg);
            mErrorCode = errorCode;
        }

        @FontManager.ResultCode
        int getErrorCode() {
            return mErrorCode;
        }
    }

    /** Class to manage FontManagerService's lifecycle. */
    public static final class Lifecycle extends SystemService {
        private final FontManagerService mService;
        private final CompletableFuture<Void> mServiceStarted = new CompletableFuture<>();

        public Lifecycle(@NonNull Context context, boolean safeMode) {
            super(context);
            mService = new FontManagerService(context, safeMode, mServiceStarted);
        }

        @Override
        public void onStart() {
            LocalServices.addService(FontManagerInternal.class,
                    new FontManagerInternal() {
                        @Override
                        @Nullable
                        public SharedMemory getSerializedSystemFontMap() {
                            if (!Typeface.ENABLE_LAZY_TYPEFACE_INITIALIZATION) {
                                return null;
                            }
                            mServiceStarted.join();
                            return mService.getCurrentFontMap();
                        }
                    });
            publishBinderService(Context.FONT_SERVICE, mService);
        }


        @Override
        public void onBootPhase(int phase) {
            final int latestFontLoadBootPhase =
                    (Flags.completeFontLoadInSystemServicesReady())
                            // Complete font load in the phase before PHASE_SYSTEM_SERVICES_READY
                            ? SystemService.PHASE_LOCK_SETTINGS_READY
                            : SystemService.PHASE_ACTIVITY_MANAGER_READY;
            if (phase == latestFontLoadBootPhase) {
                // Wait for FontManagerService to start since it will be needed after this point.
                mServiceStarted.join();
                mService.mUserFontOverlays.start();
            }
        }

        @Override
        public void onUserStarting(@NonNull TargetUser user) {
            final int userId = user.getUserIdentifier();
            mServiceStarted.thenRun(() -> mService.mUserFontOverlays.applyFontForUser(userId));
        }
    }

    private static class FsverityUtilImpl implements UpdatableFontDir.FsverityUtil {

        private final String[] mDerCertPaths;

        FsverityUtilImpl(String[] derCertPaths) {
            mDerCertPaths = derCertPaths;
        }

        @Override
        public boolean isFromTrustedProvider(String fontPath, byte[] pkcs7Signature) {
            final byte[] digest = VerityUtils.getFsverityDigest(fontPath);
            if (digest == null) {
                Log.w(TAG, "Failed to get fs-verity digest for " + fontPath);
                return false;
            }
            for (String certPath : mDerCertPaths) {
                try (InputStream is = new FileInputStream(certPath)) {
                    if (VerityUtils.verifyPkcs7DetachedSignature(pkcs7Signature, digest, is)) {
                        return true;
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed to read certificate file: " + certPath);
                }
            }
            return false;
        }

        @Override
        public void setUpFsverity(String filePath) throws IOException {
            VerityUtils.setUpFsverity(filePath);
        }

        @Override
        public boolean rename(File src, File dest) {
            // rename system call preserves fs-verity bit.
            return src.renameTo(dest);
        }
    }

    @NonNull
    private final Context mContext;

    private final boolean mIsSafeMode;

    private final Object mUpdatableFontDirLock = new Object();

    private String mDebugCertFilePath = null;

    @GuardedBy("mUpdatableFontDirLock")
    @Nullable
    private UpdatableFontDir mUpdatableFontDir;

    // mSerializedFontMapLock can be acquired while holding mUpdatableFontDirLock.
    // mUpdatableFontDirLock should not be newly acquired while holding mSerializedFontMapLock.
    private final Object mSerializedFontMapLock = new Object();

    @GuardedBy("mSerializedFontMapLock")
    @Nullable
    private SharedMemory mSerializedFontMap = null;

    @NonNull
    private final UserFontOverlays mUserFontOverlays;

    private FontManagerService(
            Context context, boolean safeMode, CompletableFuture<Void> serviceStarted) {
        if (safeMode) {
            Slog.i(TAG, "Entering safe mode. Deleting all font updates.");
            UpdatableFontDir.deleteAllFiles(new File(FONT_FILES_DIR), new File(CONFIG_XML_FILE));
        }
        mContext = context;
        mIsSafeMode = safeMode;
        mDefaultFontPackage = TextUtils.nullIfEmpty(context.getResources().getString(
                com.android.internal.R.string.config_defaultFontPackage));
        mUserFontOverlays = new UserFontOverlays(context.getContentResolver(),
                BackgroundThread.getHandler(),
                LocalServices.getService(OverlayManagerInternal.class),
                LocalServices.getService(ActivityManagerInternal.class),
                LocalServices.getService(UserManagerInternal.class),
                this::isFontFamilyInstalled, this::getDefaultFontFamilyName);

        if (Flags.useOptimizedBoottimeFontLoading()) {
            Slog.i(TAG, "Using optimized boot-time font loading.");
            SystemServerInitThreadPool.submit(() -> {
                initialize();

                // Set system font map only if it is not the preinstalled one. Otherwise
                // `initialize` will have already loaded the system font map, so there's no need
                // to set the system font map again here.
                synchronized (mUpdatableFontDirLock) {
                    if (!usesPreinstalledFontMap()) {
                        setSystemFontMap();
                    }
                }
                serviceStarted.complete(null);
            }, "FontManagerService_create");
        } else {
            Slog.i(TAG, "Not using optimized boot-time font loading.");
            initialize();
            setSystemFontMap();
            serviceStarted.complete(null);
        }
    }

    private void setSystemFontMap() {
        try {
            Typeface.setSystemFontMap(getCurrentFontMap());
        } catch (IOException | ErrnoException e) {
            Slog.w(TAG, "Failed to set system font map of system_server");
        }
    }

    @Nullable
    private UpdatableFontDir createUpdatableFontDir() {
        // Never read updatable font files in safe mode.
        if (mIsSafeMode) return null;
        // If apk verity is supported, fs-verity should be available.
        if (!VerityUtils.isFsVeritySupported()) return null;

        String[] certs = mContext.getResources().getStringArray(
                R.array.config_fontManagerServiceCerts);

        if (mDebugCertFilePath != null && Build.IS_DEBUGGABLE) {
            String[] tmp = new String[certs.length + 1];
            System.arraycopy(certs, 0, tmp, 0, certs.length);
            tmp[certs.length] = mDebugCertFilePath;
            certs = tmp;
        }

        return new UpdatableFontDir(new File(FONT_FILES_DIR), new OtfFontFileParser(),
                new FsverityUtilImpl(certs), new File(CONFIG_XML_FILE));
    }

    /**
     * Add debug certificate to the cert list. This must be called only on debuggable build.
     *
     * @param debugCertPath a debug certificate file path
     */
    public void addDebugCertificate(@Nullable String debugCertPath) {
        mDebugCertFilePath = debugCertPath;
    }

    private void initialize() {
        // Font packages do not depend on fs-verity. FontPackages.load runs before the lock is
        // taken, since it calls PackageManager, reads APK files and writes font files to /data.
        final FontPackages fontPackages = FontPackages.load(mContext.getPackageManager());
        synchronized (mUpdatableFontDirLock) {
            mFontPackages = fontPackages;
            mUpdatableFontDir = createUpdatableFontDir();
            if (usesPreinstalledFontMap()) {
                if (Flags.useOptimizedBoottimeFontLoading()) {
                    // Without fs-verity and font packages, load preinstalled system font map and
                    // use it for all apps.
                    Typeface.loadPreinstalledSystemFontMap();
                }
                setSerializedFontMap(serializeSystemServerFontMap());
            } else {
                if (mUpdatableFontDir != null) {
                    mUpdatableFontDir.loadFontFileMap();
                }
                updateSerializedFontMap();
            }
        }
        if (mDefaultFontPackage != null && getDefaultFontFamilyName() == null) {
            Slog.e(TAG, "Using the stock font, as the default font package is missing: "
                    + mDefaultFontPackage);
        }
    }

    /**
     * Whether the font map is the preinstalled system font map, as there are neither updatable
     * fonts nor font packages.
     */
    @GuardedBy("mUpdatableFontDirLock")
    private boolean usesPreinstalledFontMap() {
        return mUpdatableFontDir == null && mFontPackages.isEmpty();
    }

    @android.annotation.EnforcePermission(android.Manifest.permission.INSTALL_CUSTOM_FONTS)
    @Override
    public @NonNull Map<String, String> getCustomFontFamilyDisplayNames() {
        super.getCustomFontFamilyDisplayNames_enforcePermission();
        return mFontPackages.getDisplayNames(mContext.getPackageManager());
    }

    @NonNull
    public Context getContext() {
        return mContext;
    }

    @Nullable /* package */ SharedMemory getCurrentFontMap() {
        synchronized (mSerializedFontMapLock) {
            return mSerializedFontMap;
        }
    }

    /* package */ void update(int baseVersion, List<FontUpdateRequest> requests)
            throws SystemFontException {
        synchronized (mUpdatableFontDirLock) {
            if (mUpdatableFontDir == null) {
                throw new SystemFontException(
                        FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED,
                        "The font updater is disabled.");
            }
            // baseVersion == -1 only happens from shell command. This is filtered and treated as
            // error from SystemApi call.
            if (baseVersion != -1 && mUpdatableFontDir.getConfigVersion() != baseVersion) {
                throw new SystemFontException(
                        FontManager.RESULT_ERROR_VERSION_MISMATCH,
                        "The base config version is older than current.");
            }
            mUpdatableFontDir.update(requests);
            updateSerializedFontMap();
        }
    }

    /* package */ void updateFallbacks(List<FontUpdateRequest> fallbackRequests)
            throws SystemFontException {
        synchronized (mUpdatableFontDirLock) {
            if (mUpdatableFontDir == null) {
                throw new SystemFontException(
                        FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED,
                        "The font updater is disabled.");
            }
            mUpdatableFontDir.updateFontFallbacks(fallbackRequests);
            updateSerializedFontMap();
        }
    }

    /**
     * Clears all updates and restarts FontManagerService.
     *
     * <p>CAUTION: this method is not safe. Existing processes may crash due to missing font files.
     * This method is only for {@link FontManagerShellCommand}.
     */
    /* package */ void clearUpdates() {
        UpdatableFontDir.deleteAllFiles(new File(FONT_FILES_DIR), new File(CONFIG_XML_FILE));
        initialize();
        mUserFontOverlays.applyFontForStartedUsers();
    }

    /**
     * Restarts FontManagerService, removing not-the-latest font files.
     *
     * <p>CAUTION: this method is not safe. Existing processes may crash due to missing font files.
     * This method is only for {@link FontManagerShellCommand}.
     */
    /* package */ void restart() {
        initialize();
        mUserFontOverlays.applyFontForStartedUsers();
    }

    /* package */ Map<String, File> getFontFileMap() {
        synchronized (mUpdatableFontDirLock) {
            if (mUpdatableFontDir == null) {
                return Collections.emptyMap();
            }
            return mUpdatableFontDir.getPostScriptMap();
        }
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter writer,
            @Nullable String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, writer)) return;
        new FontManagerShellCommand(this).dumpAll(new IndentingPrintWriter(writer, "  "));
    }

    @Override
    public void onShellCommand(@Nullable FileDescriptor in,
            @Nullable FileDescriptor out,
            @Nullable FileDescriptor err,
            @NonNull String[] args,
            @Nullable ShellCallback callback,
            @NonNull ResultReceiver result) {
        new FontManagerShellCommand(this).exec(this, in, out, err, args, callback, result);
    }

    /**
     * Returns an active system font configuration.
     */
    public @NonNull FontConfig getSystemFontConfig() {
        synchronized (mUpdatableFontDirLock) {
            if (mUpdatableFontDir == null) {
                return getSystemImageFontConfig();
            }
            return mFontPackages.addFamiliesTo(mUpdatableFontDir.getSystemFontConfig());
        }
    }

    /** The preinstalled fonts and the fonts of the font packages, all from the system image. */
    private @NonNull FontConfig getSystemImageFontConfig() {
        return mFontPackages.addFamiliesTo(SystemFonts.getSystemPreinstalledFontConfig());
    }

    /**
     * Makes new serialized font map data and updates mSerializedFontMap.
     */
    private void updateSerializedFontMap() {
        SharedMemory serializedFontMap = serializeFontMap(getSystemFontConfig());
        if (serializedFontMap == null) {
            // Fallback to the fonts on the system image.
            serializedFontMap = serializeFontMap(getSystemImageFontConfig());
        }
        setSerializedFontMap(serializedFontMap);
    }

    @Nullable
    private static SharedMemory serializeFontMap(FontConfig fontConfig) {
        final ArrayMap<String, ByteBuffer> bufferCache = new ArrayMap<>();
        try {
            final Map<String, FontFamily[]> fallback =
                    SystemFonts.buildSystemFallback(fontConfig, bufferCache);
            final Map<String, Typeface> typefaceMap =
                    SystemFonts.buildSystemTypefaces(fontConfig, fallback);
            return Typeface.serializeFontMap(typefaceMap);
        } catch (IOException | ErrnoException e) {
            Slog.w(TAG, "Failed to serialize updatable font map. "
                    + "Retrying with system image fonts.", e);
            return null;
        } finally {
            // Unmap buffers promptly, as we map a lot of files and may hit mmap limit before
            // GC collects ByteBuffers and unmaps them.
            for (ByteBuffer buffer : bufferCache.values()) {
                if (buffer instanceof DirectByteBuffer) {
                    NioUtils.freeDirectBuffer(buffer);
                }
            }
        }
    }

    @Nullable
    private static SharedMemory serializeSystemServerFontMap() {
        try {
            return Typeface.serializeFontMap(Typeface.getSystemFontMap());
        } catch (IOException | ErrnoException e) {
            Slog.e(TAG, "Failed to serialize SystemServer system font map", e);
            return null;
        }
    }

    private void setSerializedFontMap(SharedMemory serializedFontMap) {
        SharedMemory oldFontMap = null;
        synchronized (mSerializedFontMapLock) {
            oldFontMap = mSerializedFontMap;
            mSerializedFontMap = serializedFontMap;
        }
        if (oldFontMap != null) {
            oldFontMap.close();
        }
    }
}
