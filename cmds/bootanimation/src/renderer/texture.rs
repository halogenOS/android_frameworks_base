// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Textures of OpenGL ES 3.0 that shaders sample.

use std::ptr;

use gl::types::{GLenum, GLint, GLsizei, GLuint};

use super::text::Atlas;
use super::Renderer;
use crate::error::Error;

/// A texture on the GPU. It borrows the `Renderer`, so it cannot outlive the context, and
/// dropping it deletes the texture.
pub struct Texture<'a> {
    id: GLuint,
    renderer: &'a Renderer,
}

// The names of OpenGL ES that TexImage2D and TexParameteri take as a GLint.
const FORMAT_R8: GLint = gl_name_parameter::<{ gl::R8 }>();
const FORMAT_RGB10_A2: GLint = gl_name_parameter::<{ gl::RGB10_A2 }>();
const WRAP_CLAMP_TO_EDGE: GLint = gl_name_parameter::<{ gl::CLAMP_TO_EDGE }>();

/// NAME, a name of OpenGL ES, as the `GLint` that `TexImage2D` and `TexParameteri` take it as.
/// The build checks NAME, so a name of 2^31 or above fails the build.
const fn gl_name_parameter<const NAME: GLenum>() -> GLint {
    const { assert!(NAME <= GLint::MAX.cast_unsigned(), "a name of OpenGL ES of 2^31 or above") };
    NAME.cast_signed()
}

/// How a texture is sampled between its texels.
#[derive(Clone, Copy, Debug)]
enum Filter {
    Nearest,
    Linear,
}

impl Filter {
    /// The filter as the `GLint` that `TexParameteri` takes it as.
    const fn parameter(self) -> GLint {
        match self {
            Self::Nearest => gl_name_parameter::<{ gl::NEAREST }>(),
            Self::Linear => gl_name_parameter::<{ gl::LINEAR }>(),
        }
    }
}

impl Renderer {
    /// A texture of the texels of atlas, of one channel. Shaders read the channel as red, from 0.0
    /// to 1.0. Each texel shows on one pixel, so the texture is sampled without filtering. Fails
    /// when the texture is larger than the limit of OpenGL ES.
    pub fn single_channel_texture(&self, atlas: &Atlas) -> Result<Texture<'_>, Error> {
        let texels = atlas.texels();
        self.texture(atlas.width(), atlas.height(), Filter::Nearest, |gl_width, gl_height| {
            // SAFETY: The context is current on this thread, asserted by Renderer::texture. With an
            // unpack alignment of 1, TexImage2D reads width x height bytes from the pointer, the
            // start of texels, which an Atlas always has.
            unsafe {
                gl::PixelStorei(gl::UNPACK_ALIGNMENT, 1);
                gl::TexImage2D(
                    gl::TEXTURE_2D,
                    0,
                    FORMAT_R8,
                    gl_width,
                    gl_height,
                    0,
                    gl::RED,
                    gl::UNSIGNED_BYTE,
                    texels.as_ptr().cast(),
                );
            }
        })
    }

    /// A texture of width x height texels for a framebuffer to draw into, with 10 bits for each of
    /// red, green and blue from 0.0 to 1.0, sampled with linear filtering. Its texels are
    /// undefined until something draws into them. Fails when OpenGL ES takes no texture this
    /// large.
    pub(super) fn drawable_texture(
        &self,
        width: usize,
        height: usize,
    ) -> Result<Texture<'_>, Error> {
        self.texture(width, height, Filter::Linear, |gl_width, gl_height| {
            // SAFETY: The context is current on this thread, asserted by Renderer::texture.
            // TexImage2D reads nothing through the null pointer and leaves the texels undefined.
            unsafe {
                gl::TexImage2D(
                    gl::TEXTURE_2D,
                    0,
                    FORMAT_RGB10_A2,
                    gl_width,
                    gl_height,
                    0,
                    gl::RGBA,
                    gl::UNSIGNED_INT_2_10_10_10_REV,
                    ptr::null(),
                );
            }
        })
    }

    /// A new texture of width x height texels, sampled with filter and clamped at its edges.
    /// allocate is called with the sides as OpenGL ES takes them, while the texture is bound to
    /// `TEXTURE_2D`, to allocate its texels. Fails when OpenGL ES takes no texture this large.
    fn texture(
        &self,
        width: usize,
        height: usize,
        filter: Filter,
        allocate: impl FnOnce(GLsizei, GLsizei),
    ) -> Result<Texture<'_>, Error> {
        self.assert_current();
        let mut limit = 0;
        // SAFETY: The context is current on this thread, asserted above. GetIntegerv writes one
        // GLint for MAX_TEXTURE_SIZE through the pointer, which points to the local limit.
        unsafe { gl::GetIntegerv(gl::MAX_TEXTURE_SIZE, &raw mut limit) };
        let side = |side: usize| GLsizei::try_from(side).ok().filter(|&side| side <= limit);
        let (Some(gl_width), Some(gl_height)) = (side(width), side(height)) else {
            return Err(Error::TextureTooLarge { width, height, limit });
        };
        let mut id = 0;
        // SAFETY: As above, with GenTextures, which writes one name through the pointer, which
        // points to the local id.
        unsafe { gl::GenTextures(1, &raw mut id) };
        let texture = Texture { id, renderer: self };
        let filter = filter.parameter();
        // SAFETY: The context is current on this thread, asserted above, and these calls read only
        // the plain values passed to them.
        unsafe {
            gl::BindTexture(gl::TEXTURE_2D, id);
            gl::TexParameteri(gl::TEXTURE_2D, gl::TEXTURE_MIN_FILTER, filter);
            gl::TexParameteri(gl::TEXTURE_2D, gl::TEXTURE_MAG_FILTER, filter);
            gl::TexParameteri(gl::TEXTURE_2D, gl::TEXTURE_WRAP_S, WRAP_CLAMP_TO_EDGE);
            gl::TexParameteri(gl::TEXTURE_2D, gl::TEXTURE_WRAP_T, WRAP_CLAMP_TO_EDGE);
        }
        allocate(gl_width, gl_height);
        // SAFETY: As above, and BindTexture reads only the plain values passed to it.
        unsafe { gl::BindTexture(gl::TEXTURE_2D, 0) };
        Ok(texture)
    }
}

impl Texture<'_> {
    /// The name of the texture in OpenGL ES.
    pub(super) fn id(&self) -> GLuint {
        self.id
    }

    /// Binds the texture to texture unit 0, the unit that a sampler uniform reads unless it is set.
    pub(super) fn bind(&self) {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above, and these calls read only
        // the plain values passed to them.
        unsafe {
            gl::ActiveTexture(gl::TEXTURE0);
            gl::BindTexture(gl::TEXTURE_2D, self.id);
        }
    }
}

impl Drop for Texture<'_> {
    fn drop(&mut self) {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above. DeleteTextures reads one
        // name from the field id.
        unsafe { gl::DeleteTextures(1, &raw const self.id) };
    }
}
