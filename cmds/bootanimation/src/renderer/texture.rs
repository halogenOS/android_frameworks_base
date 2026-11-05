// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Textures of OpenGL ES 3.0 that shaders sample.

use std::ptr;

use gl::types::{GLenum, GLint, GLsizei, GLuint};

use super::Renderer;
use crate::error::Error;

/// A texture on the GPU. It borrows the `Renderer`, so it cannot outlive the context, and
/// dropping it deletes the texture.
pub struct Texture<'a> {
    id: GLuint,
    renderer: &'a Renderer,
}

// The names of OpenGL ES that TexImage2D and TexParameteri take as a GLint.
const FORMAT_R8: GLint = gl_name_parameter(gl::R8);
const FORMAT_RGB10_A2: GLint = gl_name_parameter(gl::RGB10_A2);
const FILTER_NEAREST: GLint = gl_name_parameter(gl::NEAREST);
const FILTER_LINEAR: GLint = gl_name_parameter(gl::LINEAR);
const WRAP_CLAMP_TO_EDGE: GLint = gl_name_parameter(gl::CLAMP_TO_EDGE);

/// value, a name of OpenGL ES, as the `GLint` that `TexImage2D` and `TexParameteri` take it as.
/// Only the constants above call it, so the build fails on a name of 2^31 or above.
const fn gl_name_parameter(value: GLenum) -> GLint {
    assert!(value <= GLint::MAX.cast_unsigned(), "a name of OpenGL ES of 2^31 or above");
    value.cast_signed()
}

impl Renderer {
    /// A texture of width x height texels of one channel, filled from texels row by row from the
    /// top. Shaders read the channel as red, from 0.0 to 1.0. Each texel shows on one pixel, so
    /// the texture is sampled without filtering. Fails when texels is not width x height texels or
    /// OpenGL ES takes no texture this large.
    pub fn single_channel_texture(
        &self,
        width: usize,
        height: usize,
        texels: &[u8],
    ) -> Result<Texture<'_>, Error> {
        if width.checked_mul(height) != Some(texels.len()) {
            return Err(Error::TexelCount { width, height, texels: texels.len() });
        }
        self.texture(width, height, FILTER_NEAREST, |gl_width, gl_height| {
            // SAFETY: The context is current on this thread, asserted by Renderer::texture. With an
            // unpack alignment of 1, TexImage2D reads width x height bytes from the pointer, the
            // start of texels, which has that many bytes, checked above.
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
        self.texture(width, height, FILTER_LINEAR, |gl_width, gl_height| {
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
        filter: GLint,
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
