/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.graphics.fonts;

import android.annotation.NonNull;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.LocaleList;
import android.text.FontConfig;
import android.util.Slog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The font packages preinstalled on the system image.
 *
 * <p>A font package is a resource-only package that declares the manifest property
 * {@link #PROPERTY_FONT_PACKAGE} and stores its font files, {@code .ttf} or {@code .otf},
 * directly under {@code assets/fonts/}. Its fonts form one font family named after the package,
 * found by every app in the system font map. The font map lists the font files copied by
 * {@link FontPackageCopies}.
 */
final class FontPackages {
    private static final String TAG = "FontPackages";

    /**
     * The manifest property, on the application element, that marks a font package.
     * custom_font_package_defaults in data/fonts/packages/Android.bp adds it to every font package.
     */
    static final String PROPERTY_FONT_PACKAGE = "custom.font.FONT_PACKAGE";

    /** Locked by {@link #load}, so that two calls never write the same copy at once. */
    private static final Object sLoadLock = new Object();

    static final FontPackages EMPTY = new FontPackages(Collections.emptyMap());

    private record FontPackage(ApplicationInfo info, FontConfig.NamedFamilyList family) {}

    /** Sorted by font family name, which is the package name. */
    private final Map<String, FontPackage> mPackages;

    private FontPackages(Map<String, FontPackage> packages) {
        mPackages = packages;
    }

    /**
     * Finds the font packages on the system image, copies their fonts out of their APKs unless
     * the same fonts were already copied, and reads them. A package with a font that cannot be
     * copied or read is skipped as a whole. The copies made from skipped or missing packages are
     * deleted, so that the next boot copies them again.
     */
    static @NonNull FontPackages load(@NonNull PackageManager pm) {
        synchronized (sLoadLock) {
            final Map<String, FontPackage> packages = new TreeMap<>();
            final Set<File> keptCopyDirs = new HashSet<>();
            for (PackageManager.Property property : pm.queryApplicationProperty(
                    PROPERTY_FONT_PACKAGE)) {
                final String packageName = property.getPackageName();
                try {
                    // The factory version, so that the fonts come from the system image.
                    // getApplicationInfo does not apply MATCH_FACTORY_ONLY, getPackageInfo does.
                    final ApplicationInfo info = pm.getPackageInfo(packageName,
                            PackageManager.PackageInfoFlags.of(PackageManager.MATCH_FACTORY_ONLY))
                            .applicationInfo;
                    final FontPackageCopies.Copy copy =
                            FontPackageCopies.copyFonts(packageName, info.sourceDir);
                    final FontConfig.NamedFamilyList family = new FontConfig.NamedFamilyList(
                            Collections.singletonList(readFontFamily(copy.fontFiles())),
                            packageName /* name */, null /* fallback */);
                    packages.put(family.getName(), new FontPackage(info, family));
                    keptCopyDirs.add(copy.dir());
                } catch (PackageManager.NameNotFoundException e) {
                    Slog.w(TAG, "Ignoring font package missing from the system image: "
                            + packageName);
                } catch (IOException e) {
                    Slog.e(TAG, "Skipping font package whose fonts cannot be used: " + packageName,
                            e);
                }
            }
            FontPackageCopies.deleteCopiesExcept(keptCopyDirs);
            return new FontPackages(Collections.unmodifiableMap(packages));
        }
    }

    private static FontConfig.FontFamily readFontFamily(List<File> fontFiles) throws IOException {
        final List<FontConfig.Font> fonts = new ArrayList<>();
        for (File fontFile : fontFiles) {
            fonts.add(OtfFontFileParser.readFont(fontFile));
        }
        return new FontConfig.FontFamily(fonts, LocaleList.getEmptyLocaleList(),
                FontConfig.FontFamily.VARIANT_DEFAULT);
    }

    boolean isEmpty() {
        return mPackages.isEmpty();
    }

    /** Whether a font package has the font family {@code familyName}. */
    boolean contains(@NonNull String familyName) {
        return mPackages.containsKey(familyName);
    }

    /** The font family names, which are the package names. */
    @NonNull Set<String> getFamilyNames() {
        return mPackages.keySet();
    }

    /**
     * Maps the name of each font family to the label of the font package that provides it, in the
     * current locale.
     */
    @NonNull Map<String, String> getDisplayNames(@NonNull PackageManager pm) {
        final Map<String, String> displayNames = new TreeMap<>();
        mPackages.forEach((name, fontPackage) ->
                displayNames.put(name, pm.getApplicationLabel(fontPackage.info()).toString()));
        return displayNames;
    }

    /**
     * Returns {@code config} with one font family added for each font package. A font package
     * replaces the font family of {@code config} that has the same name.
     */
    @NonNull FontConfig addFamiliesTo(@NonNull FontConfig config) {
        final List<FontConfig.NamedFamilyList> namedFamilies =
                new ArrayList<>(config.getNamedFamilyLists());
        namedFamilies.removeIf(family -> contains(family.getName()));
        for (FontPackage fontPackage : mPackages.values()) {
            namedFamilies.add(fontPackage.family());
        }
        return new FontConfig(config.getFontFamilies(), config.getAliases(), namedFamilies,
                config.getLocaleFallbackCustomizations(), config.getLastModifiedTimeMillis(),
                config.getConfigVersion());
    }
}
