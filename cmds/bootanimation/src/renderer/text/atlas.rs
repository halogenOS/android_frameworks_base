// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The bitmaps of glyphs packed into the texels of one texture, in rows.

use super::rasterizer::Bitmap;
use crate::error::Error;

/// The empty texels between two bitmaps, so that a sample at the edge of one never reads the next.
const GAP: usize = 1;

/// Bitmaps packed into the texels of a texture.
pub struct Atlas {
    pub width: u16,
    pub height: u16,
    /// width x height texels, row by row from the top.
    pub texels: Vec<u8>,
    /// The texel of the top left corner of each bitmap, in the order the bitmaps were given.
    pub corners: Vec<[u16; 2]>,
}

impl Atlas {
    /// Packs bitmaps into rows from the top, the tallest first, in a texture about as wide as it
    /// is high. Fails when the texture would be more than 65535 texels wide or high.
    pub fn pack(bitmaps: &[&Bitmap]) -> Result<Self, Error> {
        let size = |bitmap: &Bitmap| [usize::from(bitmap.width), usize::from(bitmap.height)];
        let area: usize = bitmaps
            .iter()
            .map(|bitmap| {
                let [width, height] = size(bitmap);
                (width + GAP) * (height + GAP)
            })
            .sum();
        let widest = bitmaps.iter().map(|bitmap| size(bitmap)[0]).max().unwrap_or_default();
        let width = area.isqrt().max(widest);
        let mut order: Vec<usize> = (0..bitmaps.len()).collect();
        order.sort_by_key(|&index| std::cmp::Reverse(size(bitmaps[index])[1]));

        let mut corners = vec![[0, 0]; bitmaps.len()];
        let [mut x, mut y, mut row_height] = [0, 0, 0];
        for index in order {
            let [bitmap_width, bitmap_height] = size(bitmaps[index]);
            if x + bitmap_width > width {
                [x, y, row_height] = [0, y + row_height + GAP, 0];
            }
            corners[index] = [x, y];
            x += bitmap_width + GAP;
            row_height = row_height.max(bitmap_height);
        }
        let height = y + row_height;
        // The corners lie inside the texture, so they fit into a u16 whenever its sides do.
        let to_u16 = |texel: usize| {
            u16::try_from(texel).map_err(|cause| Error::AtlasTooLarge { width, height, cause })
        };
        let sides = [to_u16(width)?, to_u16(height)?];

        let mut texels = vec![0; width * height];
        for (bitmap, [left, top]) in bitmaps.iter().zip(&corners) {
            let [bitmap_width, _] = size(bitmap);
            for (row, coverage) in bitmap.coverage.chunks_exact(bitmap_width).enumerate() {
                let start = (top + row) * width + left;
                texels[start..start + bitmap_width].copy_from_slice(coverage);
            }
        }
        let corners = corners
            .into_iter()
            .map(|[left, top]| Ok([to_u16(left)?, to_u16(top)?]))
            .collect::<Result<_, Error>>()?;
        let [width, height] = sides;
        Ok(Self { width, height, texels, corners })
    }
}
