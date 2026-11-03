// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! A TrueType font read with skrifa: the glyphs of characters, their advances and outlines at one
//! weight and size.

use std::fs;

use skrifa::instance::{Location, Size};
use skrifa::outline::pen::PathElement;
use skrifa::outline::{DrawError, DrawSettings};
use skrifa::{FontRef, GlyphId, MetadataProvider};

use super::Style;
use crate::error::Error;

/// A font file, read whole.
pub struct Font {
    path: &'static str,
    data: Vec<u8>,
}

impl Font {
    /// Reads the font file at path.
    pub fn read(path: &'static str) -> Result<Self, Error> {
        let data = fs::read(path).map_err(|cause| Error::FontRead { path, cause })?;
        Ok(Self { path, data })
    }
}

/// A font at the weight and size of a style.
pub struct Face<'f> {
    font: FontRef<'f>,
    location: Location,
    size: Size,
}

impl<'f> Face<'f> {
    /// The font at the weight and size of style. Fails when the file holds no font.
    pub fn new(font: &'f Font, style: Style) -> Result<Self, Error> {
        let font = FontRef::new(&font.data)
            .map_err(|cause| Error::FontParse { path: font.path, cause })?;
        Ok(Self {
            location: font.axes().location([("wght", style.weight)]),
            size: Size::new(style.size),
            font,
        })
    }

    /// The glyph of character, or the glyph for missing characters when the font has none.
    pub fn glyph(&self, character: char) -> GlyphId {
        self.font.charmap().map(character).unwrap_or(GlyphId::NOTDEF)
    }

    /// How far the next glyph starts after glyph, in pixels, or None when the font has no advance
    /// for it.
    pub fn advance(&self, glyph: GlyphId) -> Option<f32> {
        self.font.glyph_metrics(self.size, &self.location).advance_width(glyph)
    }

    /// The outline of glyph in pixels, y up, from the origin of the glyph on the baseline. It is
    /// empty for a glyph without an outline, such as a space.
    pub fn outline(&self, glyph: GlyphId) -> Result<Vec<PathElement>, DrawError> {
        let mut path = Vec::new();
        if let Some(outline) = self.font.outline_glyphs().get(glyph) {
            // Only hinting adjusts the metrics.
            let _unhinted_metrics =
                outline.draw(DrawSettings::unhinted(self.size, &self.location), &mut path)?;
        }
        Ok(path)
    }
}
