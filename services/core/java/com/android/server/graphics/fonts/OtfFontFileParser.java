/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontFileUtil;
import android.graphics.fonts.FontVariationAxis;
import android.text.FontConfig;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DirectByteBuffer;
import java.nio.NioUtils;
import java.nio.channels.FileChannel;
import java.util.Set;

/* package */  class OtfFontFileParser implements UpdatableFontDir.FontFileParser {
    @Override
    public String getPostScriptName(File file) throws IOException {
        ByteBuffer buffer = mmap(file);
        try {
            return FontFileUtil.getPostScriptName(buffer, 0);
        } finally {
            unmap(buffer);
        }
    }

    @Override
    public String getFamilyName(File file) throws IOException {
        ByteBuffer buffer = mmap(file);
        try {
            return FontFileUtil.getFamilyName(buffer, 0);
        } finally {
            unmap(buffer);
        }
    }

    @Override
    public String buildFontFileName(File file) throws IOException {
        ByteBuffer buffer = mmap(file);
        try {
            String psName = FontFileUtil.getPostScriptName(buffer, 0);
            int isType1Font = FontFileUtil.isPostScriptType1Font(buffer, 0);
            int isCollection = FontFileUtil.isCollectionFont(buffer);

            if (TextUtils.isEmpty(psName) || isType1Font == -1 || isCollection == -1) {
                return null;
            }

            String extension;
            if (isCollection == 1) {
                extension = isType1Font == 1 ? ".otc" : ".ttc";
            } else {
                extension = isType1Font == 1 ? ".otf" : ".ttf";
            }
            return psName + extension;
        } finally {
            unmap(buffer);
        }

    }

    @Override
    public long getRevision(File file) throws IOException {
        ByteBuffer buffer = mmap(file);
        try {
            return FontFileUtil.getRevision(buffer, 0);
        } finally {
            unmap(buffer);
        }
    }

    @Override
    public void tryToCreateTypeface(File file) throws Throwable {
        ByteBuffer buffer = mmap(file);
        try {
            Font font = new Font.Builder(buffer).build();
            FontFamily family = new FontFamily.Builder(font).build();
            Typeface typeface = new Typeface.CustomFallbackBuilder(family).build();

            TextPaint p = new TextPaint();
            p.setTextSize(24f);
            p.setTypeface(typeface);

            // Test string to try with the passed font.
            // TODO: Good to extract from font file.
            String testTextToDraw = "abcXYZ@- "
                    + "\uD83E\uDED6" // Emoji E13.0
                    + "\uD83C\uDDFA\uD83C\uDDF8" // Emoji Flags
                    + "\uD83D\uDC8F\uD83C\uDFFB" // Emoji Skin tone Sequence
                    // ZWJ Sequence
                    + "\uD83D\uDC68\uD83C\uDFFC\u200D\u2764\uFE0F\u200D\uD83D\uDC8B\u200D"
                    + "\uD83D\uDC68\uD83C\uDFFF";

            int width = (int) Math.ceil(Layout.getDesiredWidth(testTextToDraw, p));
            StaticLayout layout = StaticLayout.Builder.obtain(
                    testTextToDraw, 0, testTextToDraw.length(), p, width).build();
            Bitmap bmp = Bitmap.createBitmap(
                    layout.getWidth(), layout.getHeight(), Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas(bmp);
            layout.draw(canvas);
        } finally {
            unmap(buffer);
        }
    }

    /** Reads the font map entry of the font file at {@code file}. */
    static FontConfig.Font readFont(File file) throws IOException {
        final ByteBuffer buffer = mmap(file);
        try {
            final String postScriptName = FontFileUtil.getPostScriptName(buffer, 0);
            if (postScriptName == null) {
                throw new IOException("PostScript name missing from font: " + file);
            }
            return new FontConfig.Font(file, null /* originalFile */, postScriptName,
                    FontFileUtil.readFontStyle(buffer), 0 /* index */,
                    "" /* fontVariationSettings */, null /* fontFamilyName */,
                    readVarTypeAxes(buffer));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            // FontFileUtil reports malformed font data through these.
            throw new IOException("Malformed font: " + file, e);
        } finally {
            unmap(buffer);
        }
    }

    /**
     * Returns the flags for the variation axes {@code wght} and {@code ital} supported by the
     * font in {@code buffer}.
     */
    private static @FontConfig.Font.VarTypeAxes int readVarTypeAxes(ByteBuffer buffer) {
        final Set<Integer> axes = FontFileUtil.getSupportedAxes(buffer, 0 /* index */);
        return (axes.contains(FontVariationAxis.makeTag("wght"))
                        ? FontConfig.Font.VAR_TYPE_AXES_WGHT : 0)
                | (axes.contains(FontVariationAxis.makeTag("ital"))
                        ? FontConfig.Font.VAR_TYPE_AXES_ITAL : 0);
    }

    private static ByteBuffer mmap(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            FileChannel fileChannel = in.getChannel();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        }
    }

    private static void unmap(ByteBuffer buffer) {
        if (buffer instanceof DirectByteBuffer) {
            NioUtils.freeDirectBuffer(buffer);
        }
    }
}
