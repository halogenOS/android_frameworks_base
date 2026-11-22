/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.graphics.fonts;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManagerInternal;
import android.content.ContentResolver;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.util.TypedValue;

import com.android.internal.util.Preconditions;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Builds the font overlay for each user from {@link Settings.Secure#CUSTOM_FONT_FAMILY}. A user
 * whose picked font family is not installed gets the default font, and the setting is left as it
 * is, so the pick applies again once the family is installed again.
 *
 * <p>Every overlay build runs on one handler, so builds commit in the order they were requested.
 */
final class UserFontOverlays {
    private static final String TAG = "UserFontOverlays";

    private static final String CUSTOM_FONT_OVERLAY_PACKAGE = "android";
    private static final String CUSTOM_FONT_OVERLAY_NAME_PREFIX = "custom_font_family_u";
    private static final String[] CUSTOM_FONT_OVERLAY_RESOURCES = {
            // Legacy config_* entries — styles reference these directly via
            // @*android:string/config_... rather than passing a hardcoded name
            // to Typeface.create, so they bypass the alias resolver.
            "android:string/config_bodyFontFamily",
            "android:string/config_bodyFontFamilyMedium",
            "android:string/config_headlineFontFamily",
            "android:string/config_headlineFontFamilyMedium",
            "android:string/config_regularFontFamily",
            "android:string/config_lightFontFamily",
            "android:string/config_clockFontFamily",
            // Alias resources — cover hardcoded fontFamily names that flow
            // through Typeface.create(String, ...) and FontFamilyResolver.
            // Sans-serif variants
            "android:string/config_fontFamilyAlias_sans_serif",
            "android:string/config_fontFamilyAlias_sans_serif_medium",
            "android:string/config_fontFamilyAlias_sans_serif_light",
            "android:string/config_fontFamilyAlias_sans_serif_thin",
            "android:string/config_fontFamilyAlias_sans_serif_black",
            "android:string/config_fontFamilyAlias_sans_serif_regular",
            "android:string/config_fontFamilyAlias_sans_serif_condensed",
            "android:string/config_fontFamilyAlias_sans_serif_condensed_medium",
            "android:string/config_fontFamilyAlias_sans_serif_condensed_light",
            // GMS / Pixel / Roboto
            "android:string/config_fontFamilyAlias_google_sans",
            "android:string/config_fontFamilyAlias_google_sans_clock",
            "android:string/config_fontFamilyAlias_google_sans_flex",
            "android:string/config_fontFamilyAlias_google_sans_medium",
            "android:string/config_fontFamilyAlias_google_sans_text",
            "android:string/config_fontFamilyAlias_google_sans_text_medium",
            "android:string/config_fontFamilyAlias_roboto_regular",
            "android:string/config_fontFamilyAlias_font_family_flex_device_default",
            // Variable-axis: display
            "android:string/config_fontFamilyAlias_variable_display_large",
            "android:string/config_fontFamilyAlias_variable_display_large_emphasized",
            "android:string/config_fontFamilyAlias_variable_display_medium",
            "android:string/config_fontFamilyAlias_variable_display_medium_emphasized",
            "android:string/config_fontFamilyAlias_variable_display_small",
            "android:string/config_fontFamilyAlias_variable_display_small_emphasized",
            // Variable-axis: headline
            "android:string/config_fontFamilyAlias_variable_headline_large",
            "android:string/config_fontFamilyAlias_variable_headline_large_emphasized",
            "android:string/config_fontFamilyAlias_variable_headline_medium",
            "android:string/config_fontFamilyAlias_variable_headline_medium_emphasized",
            "android:string/config_fontFamilyAlias_variable_headline_small",
            "android:string/config_fontFamilyAlias_variable_headline_small_emphasized",
            // Variable-axis: title
            "android:string/config_fontFamilyAlias_variable_title_large",
            "android:string/config_fontFamilyAlias_variable_title_large_emphasized",
            "android:string/config_fontFamilyAlias_variable_title_medium",
            "android:string/config_fontFamilyAlias_variable_title_medium_emphasized",
            "android:string/config_fontFamilyAlias_variable_title_small",
            "android:string/config_fontFamilyAlias_variable_title_small_emphasized",
            // Variable-axis: label
            "android:string/config_fontFamilyAlias_variable_label_large",
            "android:string/config_fontFamilyAlias_variable_label_large_emphasized",
            "android:string/config_fontFamilyAlias_variable_label_medium",
            "android:string/config_fontFamilyAlias_variable_label_medium_emphasized",
            "android:string/config_fontFamilyAlias_variable_label_small",
            "android:string/config_fontFamilyAlias_variable_label_small_emphasized",
            // Variable-axis: body
            "android:string/config_fontFamilyAlias_variable_body_large",
            "android:string/config_fontFamilyAlias_variable_body_large_emphasized",
            "android:string/config_fontFamilyAlias_variable_body_medium",
            "android:string/config_fontFamilyAlias_variable_body_medium_emphasized",
            "android:string/config_fontFamilyAlias_variable_body_small",
            "android:string/config_fontFamilyAlias_variable_body_small_emphasized",
    };

    @NonNull
    private final ContentResolver mContentResolver;
    @NonNull
    private final Handler mHandler;
    @NonNull
    private final OverlayManagerInternal mOverlayManager;
    @NonNull
    private final ActivityManagerInternal mActivityManager;
    @NonNull
    private final UserManagerInternal mUserManager;
    @NonNull
    private final Predicate<String> mIsFontFamilyInstalled;
    /** Supplies the font family of the default font, or {@code null} for the stock font. */
    @NonNull
    private final Supplier<String> mDefaultFontFamily;

    UserFontOverlays(@NonNull ContentResolver contentResolver, @NonNull Handler handler,
            @NonNull OverlayManagerInternal overlayManager,
            @NonNull ActivityManagerInternal activityManager,
            @NonNull UserManagerInternal userManager,
            @NonNull Predicate<String> isFontFamilyInstalled,
            @NonNull Supplier<String> defaultFontFamily) {
        mContentResolver = contentResolver;
        mHandler = handler;
        mOverlayManager = Objects.requireNonNull(overlayManager);
        mActivityManager = Objects.requireNonNull(activityManager);
        mUserManager = Objects.requireNonNull(userManager);
        mIsFontFamilyInstalled = isFontFamilyInstalled;
        mDefaultFontFamily = defaultFontFamily;
    }

    /** Rebuilds the overlay of a user on every change to the pick, and removes it with the user. */
    void start() {
        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.CUSTOM_FONT_FAMILY),
                false /* notifyForDescendants */,
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange, @NonNull Collection<Uri> uris,
                            int flags, int userId) {
                        applyFont(userId);
                    }
                },
                UserHandle.USER_ALL);
        mUserManager.addUserLifecycleListener(new UserManagerInternal.UserLifecycleListener() {
            @Override
            public void onUserRemoved(UserInfo user) {
                mHandler.post(() -> commitFontOverlay(null /* familyName */, user.id));
            }
        });
    }

    /** Builds the overlay of a user that starts. */
    void applyFontForUser(int userId) {
        mHandler.post(() -> applyFont(userId));
    }

    /** Builds the overlay of every started user, after the installed font families changed. */
    void applyFontForStartedUsers() {
        mHandler.post(() -> {
            for (int userId : mActivityManager.getStartedUserIds()) {
                applyFont(userId);
            }
        });
    }

    /** The font family the user picked, or {@code null} when it is not installed or unset. */
    @Nullable
    String getInstalledPick(int userId) {
        final String picked = Binder.withCleanCallingIdentity(() -> Settings.Secure
                .getStringForUser(mContentResolver, Settings.Secure.CUSTOM_FONT_FAMILY, userId));
        return picked != null && mIsFontFamilyInstalled.test(picked) ? picked : null;
    }

    /** Saves the pick of a user, {@code null} for the default font. Returns whether it saved. */
    boolean savePick(int userId, @Nullable String familyName) {
        return Binder.withCleanCallingIdentity(() -> Settings.Secure.putStringForUser(
                mContentResolver, Settings.Secure.CUSTOM_FONT_FAMILY, familyName, userId));
    }

    private void applyFont(int userId) {
        final String installedPick = getInstalledPick(userId);
        commitFontOverlay(installedPick != null ? installedPick : mDefaultFontFamily.get(), userId);
    }

    /** Applies {@code familyName} through a fabricated overlay, or removes it when {@code null}. */
    private void commitFontOverlay(@Nullable String familyName, int userId) {
        Preconditions.checkState(mHandler.getLooper().isCurrentThread());
        // One fabricated overlay per user — name embeds the user id so overlays do not collide.
        final String overlayName = CUSTOM_FONT_OVERLAY_NAME_PREFIX + userId;
        final OverlayIdentifier id = new OverlayIdentifier(
                CUSTOM_FONT_OVERLAY_PACKAGE, overlayName);

        if (familyName == null) {
            commit(new OverlayManagerTransaction.Builder()
                    .unregisterFabricatedOverlay(id)
                    .build(), userId);
            return;
        }

        final FabricatedOverlay overlay = new FabricatedOverlay(
                overlayName, CUSTOM_FONT_OVERLAY_PACKAGE);
        overlay.setOwningPackage(CUSTOM_FONT_OVERLAY_PACKAGE);
        for (String resName : CUSTOM_FONT_OVERLAY_RESOURCES) {
            overlay.setResourceValue(resName, TypedValue.TYPE_STRING, familyName,
                    null /* configuration */);
        }
        commit(new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(overlay)
                .setEnabled(id, true, userId)
                .build(), userId);
    }

    private void commit(@NonNull OverlayManagerTransaction transaction, int userId) {
        try {
            mOverlayManager.commit(transaction);
        } catch (SecurityException e) {
            // OverlayManagerService reports a refused transaction as SecurityException.
            Slog.wtf(TAG, "Overlay manager refused the font overlay for user " + userId, e);
        }
    }
}
