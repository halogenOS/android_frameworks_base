// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The bitmaps of glyphs packed into the texels of one texture, in rows.

use std::cmp::Reverse;

use super::rasterizer::Bitmap;

/// The empty texels between two bitmaps, so that a sample at the edge of one never reads the next.
const GAP: usize = 1;

/// Bitmaps packed into the texels of a texture. Only `pack` makes one, so it always has width x
/// height texels.
pub struct Atlas {
    width: usize,
    height: usize,
    /// width x height texels, row by row from the top.
    texels: Vec<u8>,
    /// The texel of the top left corner of each bitmap, in the order the bitmaps were given.
    corners: Vec<[usize; 2]>,
}

impl Atlas {
    /// Packs bitmaps into rows from the top, the tallest first, in a texture about as wide as it
    /// is high.
    pub fn pack(bitmaps: &[&Bitmap]) -> Self {
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
        let mut order: Vec<(usize, &Bitmap)> = bitmaps.iter().copied().enumerate().collect();
        order.sort_by_key(|&(_, bitmap)| Reverse(bitmap.height));

        let mut placed = Vec::with_capacity(bitmaps.len());
        let [mut x, mut y, mut row_height] = [0, 0, 0];
        for (index, bitmap) in order {
            let [bitmap_width, bitmap_height] = size(bitmap);
            if x + bitmap_width > width {
                [x, y, row_height] = [0, y + row_height + GAP, 0];
            }
            placed.push((index, [x, y]));
            x += bitmap_width + GAP;
            row_height = row_height.max(bitmap_height);
        }
        let height = y + row_height;
        placed.sort_by_key(|&(index, _)| index);
        let corners: Vec<[usize; 2]> = placed.into_iter().map(|(_, corner)| corner).collect();

        let mut texels = vec![0; width * height];
        for (bitmap, &[left, top]) in bitmaps.iter().zip(&corners) {
            let rows = texels.chunks_exact_mut(width).skip(top);
            for (row, coverage) in rows.zip(bitmap.coverage.chunks_exact(size(bitmap)[0])) {
                for (texel, &value) in row.iter_mut().skip(left).zip(coverage) {
                    *texel = value;
                }
            }
        }
        Self { width, height, texels, corners }
    }

    pub fn width(&self) -> usize {
        self.width
    }

    pub fn height(&self) -> usize {
        self.height
    }

    /// width x height texels, row by row from the top.
    pub fn texels(&self) -> &[u8] {
        &self.texels
    }

    /// The texel of the top left corner of each bitmap, in the order the bitmaps were given.
    pub fn corners(&self) -> &[[usize; 2]] {
        &self.corners
    }
}
