// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The place, the size and the shape of the logo and the tiles that its triangles are cut into,
//! used by the swarm that builds it and by the glass shaders that draw it.
//!
//! The logo is two triangles of the same shape: a blue one pointing left, and a light blue-white
//! one pointing right in front of it. Each triangle lies around its home, a point on the axis of
//! the logo, `TRIANGLE_OVERLAP / 2` logo sizes from the center of the logo towards the back of
//! the triangle.

use std::iter;

use crate::renderer::ShaderConstant;

/// The height of the center of the logo, in screen heights from the center of the screen, y up.
pub const LOGO_CENTER_Y: f32 = 0.04;
/// The size of the logo in screen heights. The shape of the triangles is measured in it.
pub const LOGO_SIZE: f32 = 0.094;
/// The rows of tiles in each triangle of the logo.
pub const LOGO_ROWS: u16 = 15;
/// The part of the logo size by which the two triangles overlap.
pub const TRIANGLE_OVERLAP: f32 = 0.1;
/// Where the tip of the blue triangle lies on the axis of the logo, in logo sizes right of its
/// home. The white triangle is its mirror image.
pub const TRIANGLE_TIP: f32 = -1.0;
/// Where the back of the blue triangle lies on the axis of the logo, in logo sizes right of its
/// home.
pub const TRIANGLE_BACK: f32 = 0.5;
/// How far the two back corners of a triangle lie above and below the axis of the logo, in logo
/// sizes.
pub const TRIANGLE_HALF_HEIGHT: f32 = 0.8;

/// The values of this module that the glass shaders use, under the same names.
pub const SHADER_CONSTANTS: [ShaderConstant; 6] = [
    ShaderConstant::Float("LOGO_CENTER_Y", LOGO_CENTER_Y),
    ShaderConstant::Float("LOGO_SIZE", LOGO_SIZE),
    ShaderConstant::Float("TRIANGLE_OVERLAP", TRIANGLE_OVERLAP),
    ShaderConstant::Float("TRIANGLE_TIP", TRIANGLE_TIP),
    ShaderConstant::Float("TRIANGLE_BACK", TRIANGLE_BACK),
    ShaderConstant::Float("TRIANGLE_HALF_HEIGHT", TRIANGLE_HALF_HEIGHT),
];

/// One of the two triangles of the logo.
#[derive(Clone, Copy, Debug)]
pub enum Piece {
    Blue,
    White,
}

/// How a tile lies in its triangle.
#[derive(Clone, Copy, Debug)]
pub enum Orientation {
    /// Upright like the triangle.
    Upright,
    /// Upside down, between two upright tiles.
    UpsideDown,
}

impl Orientation {
    /// The factor that turns the corners of an upright tile into the corners of a tile of this
    /// orientation around the same center.
    pub fn scale(self) -> f32 {
        match self {
            Self::Upright => 1.0,
            Self::UpsideDown => -1.0,
        }
    }
}

/// A triangle of the logo, in the unit of the logo size it was made for, from the center of the
/// logo, y down. Places relative to the center keep the tiles on the axis of the logo exactly on
/// it, so their angle around the center, and with it the order of the tiles, comes out exact.
pub struct Triangle {
    pub piece: Piece,
    tip: [f32; 2],
    top: [f32; 2],
    bottom: [f32; 2],
}

impl Triangle {
    /// The blue triangle, pointing left, and the light blue-white one, pointing right, of the logo
    /// of size.
    pub fn of_logo(size: f32) -> [Self; 2] {
        // The triangle around the home at side times shift, mirrored for side -1.
        let shift = size * TRIANGLE_OVERLAP / 2.0;
        let triangle = |piece, side: f32| {
            let along_axis = |place: f32| side * (shift + place * size);
            Self {
                piece,
                tip: [along_axis(TRIANGLE_TIP), 0.0],
                top: [along_axis(TRIANGLE_BACK), -TRIANGLE_HALF_HEIGHT * size],
                bottom: [along_axis(TRIANGLE_BACK), TRIANGLE_HALF_HEIGHT * size],
            }
        };
        [triangle(Piece::Blue, 1.0), triangle(Piece::White, -1.0)]
    }

    /// The edges from the tip to the top corner and to the bottom corner.
    fn edges(&self) -> [[f32; 2]; 2] {
        [self.top, self.bottom].map(|[x, y]| [x - self.tip[0], y - self.tip[1]])
    }

    /// The corners of a tile of the triangle, around the center of the tile.
    pub fn tile_corners(&self) -> [[f32; 2]; 3] {
        let [toward_top, toward_bottom] = self.edges();
        let thirds = 3.0 * f32::from(LOGO_ROWS);
        [[-1.0, -1.0], [2.0, -1.0], [-1.0, 2.0]].map(|[along_top, along_bottom]| {
            [
                (along_top * toward_top[0] + along_bottom * toward_bottom[0]) / thirds,
                (along_top * toward_top[1] + along_bottom * toward_bottom[1]) / thirds,
            ]
        })
    }

    /// The centers of the tiles that the triangle is cut into, `LOGO_ROWS` along each edge, each
    /// with the orientation of its tile.
    pub fn tile_centers(&self) -> Vec<([f32; 2], Orientation)> {
        let [toward_top, toward_bottom] = self.edges();
        let thirds = 3.0 * f32::from(LOGO_ROWS);
        // The point along_top and along_bottom thirds of a row along the edges from the tip.
        let at = |along_top: u16, along_bottom: u16| {
            let [a, b] = [along_top, along_bottom].map(|steps| f32::from(steps) / thirds);
            [
                self.tip[0] + a * toward_top[0] + b * toward_bottom[0],
                self.tip[1] + a * toward_top[1] + b * toward_bottom[1],
            ]
        };
        (0..LOGO_ROWS)
            .flat_map(|i| {
                (0..LOGO_ROWS - i).flat_map(move |j| {
                    let upright = (at(3 * i + 1, 3 * j + 1), Orientation::Upright);
                    let upside_down = (i + j + 1 < LOGO_ROWS)
                        .then(|| (at(3 * i + 2, 3 * j + 2), Orientation::UpsideDown));
                    iter::once(upright).chain(upside_down)
                })
            })
            .collect()
    }

    /// The place of a point of the triangle from its tip to its back, 0 to 1.
    pub fn fraction_from_tip(&self, [x, _]: [f32; 2]) -> f32 {
        (x - self.tip[0]).abs() / (self.top[0] - self.tip[0]).abs()
    }

    /// The place of a point of the triangle in the gradient of its colors, from 0 at its top left
    /// to 1 at its bottom right.
    pub fn gradient_at(&self, [x, y]: [f32; 2]) -> f32 {
        let left = self.tip[0].min(self.top[0]);
        let right = self.tip[0].max(self.top[0]);
        let across = (x - left) / (right - left);
        let down = (y - self.top[1]) / (self.bottom[1] - self.top[1]);
        f32::midpoint(across, down)
    }
}
