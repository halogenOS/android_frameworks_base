// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Text drawn from the outlines of a TrueType font. A label rasterizes the glyphs of the texts it
//! can show into a texture once, then draws a text as one textured quad per glyph, placed by the
//! advances of the font.

mod atlas;
mod font;
mod rasterizer;

use std::collections::{BTreeSet, HashMap};

use num_traits::ToPrimitive;

use self::atlas::Atlas;
use self::font::Face;
pub use self::font::Font;
use self::rasterizer::{rasterize, Bitmap, TooLarge};
use super::texture::Texture;
use super::{Blending, Mesh, Pixels, Program, Renderer, Uniform};
use crate::error::Error;

const TEXT_VERTEX: &str = include_str!("../shaders/text_vertex.glsl");
const TEXT_FRAGMENT: &str = include_str!("../shaders/text_fragment.glsl");

/// The weight and size of the glyphs of a label.
#[derive(Clone, Copy, Debug)]
pub struct Style {
    /// The weight on the wght axis of the font, 400 for regular.
    pub weight: f32,
    /// The size in pixels per em.
    pub size: f32,
}

/// Where a text lies along its baseline relative to the anchor of its label.
#[derive(Clone, Copy, Debug)]
pub enum Align {
    /// The text ends at the anchor.
    End,
    /// The middle of the text is at the anchor.
    Center,
}

impl Align {
    /// Where a text width pixels wide starts when it is aligned so to anchor.
    fn start(self, anchor: f32, width: f32) -> f32 {
        match self {
            Self::End => anchor - width,
            Self::Center => anchor - width / 2.0,
        }
    }
}

/// The coverage of a glyph on the window and in the texture of its label.
#[derive(Clone, Copy, Debug)]
struct Sprite {
    /// The top left corner from the origin of the glyph on the baseline, in whole pixels, y down.
    offset: [f32; 2],
    /// The width and height in pixels.
    size: [f32; 2],
    /// The left, top, right and bottom edge in the texture, from 0 to 1.
    texture: [f32; 4],
}

impl Sprite {
    /// The sprite of bitmap, which lies in atlas with its top left corner at the texel corner.
    fn of(bitmap: &Bitmap, atlas: &Atlas, [left, top]: [usize; 2]) -> Self {
        let in_texture = |texel: usize, side: usize| {
            texel.to_f32().expect("a usize converts to f32") / side.to_f32().expect("as above")
        };
        let [right, bottom] = [left + usize::from(bitmap.width), top + usize::from(bitmap.height)];
        Self {
            offset: bitmap.offset,
            size: [f32::from(bitmap.width), f32::from(bitmap.height)],
            texture: [
                in_texture(left, atlas.width),
                in_texture(top, atlas.height),
                in_texture(right, atlas.width),
                in_texture(bottom, atlas.height),
            ],
        }
    }
}

/// A glyph of a font with its coverage, before it has a place in a texture.
struct Rasterized {
    character: char,
    advance: f32,
    /// The coverage, or None for a glyph that covers nothing, such as a space.
    bitmap: Option<Bitmap>,
}

impl Rasterized {
    /// The glyph of character in face.
    fn of(face: &Face<'_>, character: char) -> Result<Self, Error> {
        let id = face.glyph(character);
        let advance = face.advance(id).ok_or(Error::GlyphAdvance { character })?;
        let outline = face.outline(id).map_err(|cause| Error::GlyphOutline { character, cause })?;
        let bitmap = rasterize(&outline).map_err(|TooLarge| Error::GlyphTooLarge { character })?;
        Ok(Self { character, advance, bitmap })
    }
}

/// A glyph placed on the window, in the layout of the text shader: `aPlace` and `aTexture`.
#[derive(Clone, Copy, Debug)]
struct PlacedGlyph {
    /// The left, top, right and bottom edge on the window, in pixels from the top left corner.
    place: [f32; 4],
    /// The left, top, right and bottom edge in the texture, from 0 to 1.
    texture: [f32; 4],
}

impl PlacedGlyph {
    /// The vertex attributes of the glyph for the text shader.
    fn attributes(self) -> [[f32; 4]; 2] {
        [self.place, self.texture]
    }
}

/// A glyph of a label.
#[derive(Clone, Copy, Debug)]
struct Glyph {
    /// How far the next glyph starts after this one, in pixels.
    advance: f32,
    /// The coverage, or None for a glyph that covers nothing, such as a space.
    sprite: Option<Sprite>,
}

/// A line of text in one style at an anchor on the window, whose text can change from frame to
/// frame. It holds the glyphs of the characters of the texts it was made for.
pub struct Label<'a> {
    /// The renderer whose context builds the glyphs of each text.
    renderer: &'a Renderer,
    texture: Texture<'a>,
    glyphs: HashMap<char, Glyph>,
    anchor: Pixels,
    align: Align,
    /// The text shown and its glyphs, or None before the first text.
    shown: Option<(String, Mesh<'a>)>,
}

impl<'a> Label<'a> {
    /// A label in style of font, with its baseline through anchor, aligned to it by align. It can
    /// show texts made of the characters of texts.
    pub fn new<'t>(
        renderer: &'a Renderer,
        font: &Font,
        style: Style,
        texts: impl IntoIterator<Item = &'t str>,
        anchor: Pixels,
        align: Align,
    ) -> Result<Self, Error> {
        let face = Face::new(font, style)?;
        let characters: BTreeSet<char> = texts.into_iter().flat_map(str::chars).collect();
        let rasterized = characters
            .into_iter()
            .map(|character| Rasterized::of(&face, character))
            .collect::<Result<Vec<_>, _>>()?;
        let with_bitmap: Vec<(char, &Bitmap)> = rasterized
            .iter()
            .filter_map(|glyph| Some((glyph.character, glyph.bitmap.as_ref()?)))
            .collect();
        let atlas = Atlas::pack(&with_bitmap.iter().map(|&(_, bitmap)| bitmap).collect::<Vec<_>>());
        let texture = renderer.single_channel_texture(atlas.width, atlas.height, &atlas.texels)?;
        let sprites: HashMap<char, Sprite> = with_bitmap
            .iter()
            .zip(&atlas.corners)
            .map(|(&(character, bitmap), &corner)| (character, Sprite::of(bitmap, &atlas, corner)))
            .collect();
        let glyphs: HashMap<char, Glyph> = rasterized
            .iter()
            .map(|glyph| {
                let sprite = sprites.get(&glyph.character).copied();
                (glyph.character, Glyph { advance: glyph.advance, sprite })
            })
            .collect();
        Ok(Self { renderer, texture, glyphs, anchor, align, shown: None })
    }

    /// Shows text from now on. Its glyphs are placed again only when it changed. Fails on a
    /// character outside the texts the label was made for, and then shows the previous text.
    pub fn show(&mut self, text: &str) -> Result<(), Error> {
        if self.shown.as_ref().is_some_and(|(shown, _)| shown == text) {
            return Ok(());
        }
        let attributes: Vec<_> =
            self.lay_out(text)?.into_iter().map(PlacedGlyph::attributes).collect();
        self.shown = Some((text.to_owned(), self.renderer.quad_sprites(&attributes)));
        Ok(())
    }

    /// The glyphs of text, placed along the baseline by their advances and aligned to the anchor.
    /// Glyphs that cover nothing, such as spaces, are left out. Fails on a character outside the
    /// texts the label was made for.
    fn lay_out(&self, text: &str) -> Result<Vec<PlacedGlyph>, Error> {
        let mut pen = 0.0;
        let mut placed = Vec::new();
        for character in text.chars() {
            let glyph = self.glyphs.get(&character).ok_or(Error::UnshownCharacter { character })?;
            placed.extend(glyph.sprite.map(|sprite| (pen, sprite)));
            pen += glyph.advance;
        }
        let start = self.align.start(self.anchor.x, pen);
        // Glyphs at whole pixels show each texel of the texture on one pixel.
        let baseline = self.anchor.y.round();
        Ok(placed
            .into_iter()
            .map(|(pen, sprite)| {
                let left = (start + pen).round() + sprite.offset[0];
                let top = baseline + sprite.offset[1];
                PlacedGlyph {
                    place: [left, top, left + sprite.size[0], top + sprite.size[1]],
                    texture: sprite.texture,
                }
            })
            .collect())
    }
}

/// The program that draws labels over the window.
pub struct TextProgram<'a> {
    program: Program<'a>,
    color: Uniform,
}

impl<'a> TextProgram<'a> {
    /// Builds the program for the window of renderer.
    pub fn new(renderer: &'a Renderer) -> Result<Self, Error> {
        let program = renderer.link_program("text", &[TEXT_VERTEX], &[TEXT_FRAGMENT])?;
        let resolution = program.uniform(c"uRes")?;
        let window = renderer.window();
        program.set_uniforms(|set| set.vec2(resolution, [window.width, window.height]));
        Ok(Self { color: program.uniform(c"uColor")?, program })
    }

    /// Draws the text that label shows over the window, in the red, green, blue and alpha of
    /// color, each from 0 to 1, with the alpha not multiplied into the others. A label that shows
    /// no text yet draws nothing.
    pub fn draw(&self, label: &Label<'_>, [red, green, blue, alpha]: [f32; 4]) {
        let Some((_, glyphs)) = &label.shown else { return };
        self.program.set_uniforms(|set| {
            set.vec4(self.color, [red * alpha, green * alpha, blue * alpha, alpha]);
        });
        self.program.draw_textured(glyphs, &label.texture, Blending::PremultipliedOver);
    }
}
