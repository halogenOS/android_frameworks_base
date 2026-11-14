/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.graphics.fonts;

import android.annotation.NonNull;
import android.os.FileUtils;
import android.system.ErrnoException;
import android.system.Os;
import android.util.PackageUtils;
import android.util.Slog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The font files copied out of the APKs of font packages, under {@link #COPIES_DIR}.
 *
 * <p>Apps open the font files listed in the font map by path, so the fonts of each font package
 * are copied into real, read-only files, in {@code <package name>/<content key>/}. The content key
 * changes with the font files of the APK, so an updated package gets a fresh copy.
 */
final class FontPackageCopies {
    private static final String TAG = "FontPackageCopies";

    private static final String FONT_ENTRY_PREFIX = "assets/fonts/";

    /** Created by init, readable by every app, written by system_server only. */
    private static final File COPIES_DIR = new File("/data/fonts/packages");

    /** Lets every app open a copied file by path, without listing the directories. */
    private static final int DIR_MODE = FileUtils.S_IRWXU | FileUtils.S_IXGRP | FileUtils.S_IXOTH;
    private static final int FILE_MODE = FileUtils.S_IRUSR | FileUtils.S_IRGRP | FileUtils.S_IROTH;

    /** The font files copied from one APK, and the directory that holds them. */
    record Copy(@NonNull File dir, @NonNull List<File> fontFiles) {}

    private FontPackageCopies() {}

    /**
     * Returns the font files copied from the APK at {@code apkPath}. They are copied unless every
     * one of them was already copied in full.
     */
    static @NonNull Copy copyFonts(@NonNull String packageName, @NonNull String apkPath)
            throws IOException {
        try (ZipFile apk = new ZipFile(apkPath)) {
            final List<ZipEntry> entries = readFontEntries(apk);
            final File packageDir = new File(COPIES_DIR, packageName);
            final File copyDir = new File(packageDir, computeContentKey(entries));
            if (!isCopiedInFull(entries, copyDir)) {
                deleteTree(copyDir);
                writeCopy(apk, entries, packageDir, copyDir);
            }
            return new Copy(copyDir,
                    entries.stream().map(entry -> new File(copyDir, getFileName(entry))).toList());
        }
    }

    /**
     * Deletes every copy that is not in {@code keptCopyDirs}, and every package directory left
     * empty.
     */
    static void deleteCopiesExcept(@NonNull Set<File> keptCopyDirs) {
        for (File packageDir : FileUtils.listFilesOrEmpty(COPIES_DIR)) {
            for (File copyDir : FileUtils.listFilesOrEmpty(packageDir)) {
                if (!keptCopyDirs.contains(copyDir)) {
                    deleteTree(copyDir);
                }
            }
            if (FileUtils.listFilesOrEmpty(packageDir).length == 0) {
                deleteTree(packageDir);
            }
        }
    }

    /**
     * Returns the font files of {@code apk}, sorted by name, and logs every other file under
     * {@link #FONT_ENTRY_PREFIX}.
     */
    private static List<ZipEntry> readFontEntries(ZipFile apk) throws IOException {
        final List<ZipEntry> entries = new ArrayList<>();
        for (ZipEntry entry : Collections.list(apk.entries())) {
            if (!entry.getName().startsWith(FONT_ENTRY_PREFIX)) {
                continue;
            }
            if (isFontFileName(getFileName(entry))) {
                entries.add(entry);
            } else {
                Slog.w(TAG, "Ignoring " + entry.getName() + " in " + apk.getName()
                        + ", as only .ttf and .otf files directly under " + FONT_ENTRY_PREFIX
                        + " are read");
            }
        }
        if (entries.isEmpty()) {
            throw new IOException(
                    "Font files missing from " + FONT_ENTRY_PREFIX + " in " + apk.getName());
        }
        entries.sort(Comparator.comparing(ZipEntry::getName));
        return entries;
    }

    private static boolean isFontFileName(String fileName) {
        return fileName.indexOf('/') < 0
                && (fileName.endsWith(".ttf") || fileName.endsWith(".otf"));
    }

    private static String getFileName(ZipEntry entry) {
        return entry.getName().substring(FONT_ENTRY_PREFIX.length());
    }

    /**
     * Returns a key computed from the names, sizes and CRC-32 checksums of the font files in
     * {@code entries}.
     */
    private static String computeContentKey(List<ZipEntry> entries) throws IOException {
        final StringBuilder description = new StringBuilder();
        for (ZipEntry entry : entries) {
            description.append(entry.getName()).append('\0').append(entry.getSize()).append('\0')
                    .append(entry.getCrc()).append('\0');
        }
        final String key = PackageUtils.computeSha256Digest(
                description.toString().getBytes(StandardCharsets.UTF_8));
        if (key == null) {
            throw new IOException("SHA-256 is unavailable");
        }
        return key;
    }

    /** Whether every font file of {@code entries} is in {@code copyDir}, at its full size. */
    private static boolean isCopiedInFull(List<ZipEntry> entries, File copyDir) {
        return entries.stream().allMatch(
                entry -> new File(copyDir, getFileName(entry)).length() == entry.getSize());
    }

    /**
     * Writes the font files of {@code entries} into a temporary directory and renames it to
     * {@code copyDir} once complete, so that a copy interrupted by a reboot is never used.
     */
    private static void writeCopy(ZipFile apk, List<ZipEntry> entries, File packageDir,
            File copyDir) throws IOException {
        createAppReadableDir(packageDir);
        final File tempDir = new File(packageDir, copyDir.getName() + ".tmp");
        deleteTree(tempDir);
        createAppReadableDir(tempDir);
        for (ZipEntry entry : entries) {
            final File file = new File(tempDir, getFileName(entry));
            try (InputStream in = apk.getInputStream(entry);
                    FileOutputStream out = new FileOutputStream(file)) {
                FileUtils.copy(in, out);
                out.getFD().sync();
            }
            chmod(file, FILE_MODE);
        }
        if (!tempDir.renameTo(copyDir)) {
            throw new IOException("Failed to rename " + tempDir + " to " + copyDir);
        }
    }

    /** Creates {@code dir} unless it exists, with the mode that lets apps open the files in it. */
    private static void createAppReadableDir(File dir) throws IOException {
        if (!dir.mkdir() && !dir.isDirectory()) {
            throw new IOException("Failed to create " + dir);
        }
        chmod(dir, DIR_MODE);
    }

    private static void chmod(File file, int mode) throws IOException {
        try {
            Os.chmod(file.getPath(), mode);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        }
    }

    /** Deletes {@code file} with everything in it, and logs a failure. */
    private static void deleteTree(File file) {
        if (!FileUtils.deleteContentsAndDir(file) && file.exists()) {
            Slog.w(TAG, "Failed to delete " + file);
        }
    }
}
