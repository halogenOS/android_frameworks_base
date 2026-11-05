// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Textures of OpenGL ES 3.0 that programs draw into, for later draws to sample.

use gl::types::GLuint;

use super::texture::Texture;
use super::Renderer;
use crate::error::Error;

/// A texture as large as the window and the framebuffer that draws into it. It borrows the
/// `Renderer`, so it cannot outlive the context, and dropping it deletes both.
pub struct RenderTarget<'a> {
    framebuffer: GLuint,
    texture: Texture<'a>,
    renderer: &'a Renderer,
}

impl Renderer {
    /// A render target as large as the window. Its texture has 10 bits for each of red, green and
    /// blue from 0.0 to 1.0 and is sampled with linear filtering. Fails when OpenGL ES takes no
    /// texture this large or cannot draw into it.
    pub fn window_render_target(&self) -> Result<RenderTarget<'_>, Error> {
        let size = self.layer.size();
        let texture =
            self.drawable_texture(usize::from(size.width.get()), usize::from(size.height.get()))?;
        let mut framebuffer = 0;
        // SAFETY: The context is current on this thread, asserted by drawable_texture.
        // GenFramebuffers writes one name through the pointer, which points to the local
        // framebuffer.
        unsafe { gl::GenFramebuffers(1, &raw mut framebuffer) };
        let target = RenderTarget { framebuffer, texture, renderer: self };
        // SAFETY: As above, and these calls read only the plain values passed to them.
        let status = unsafe {
            gl::BindFramebuffer(gl::FRAMEBUFFER, framebuffer);
            gl::FramebufferTexture2D(
                gl::FRAMEBUFFER,
                gl::COLOR_ATTACHMENT0,
                gl::TEXTURE_2D,
                target.texture.id(),
                0,
            );
            let status = gl::CheckFramebufferStatus(gl::FRAMEBUFFER);
            gl::BindFramebuffer(gl::FRAMEBUFFER, 0);
            status
        };
        if status == gl::FRAMEBUFFER_COMPLETE {
            Ok(target)
        } else {
            Err(Error::IncompleteFramebuffer { status })
        }
    }
}

impl<'a> RenderTarget<'a> {
    /// Calls draw with its draws going into the texture of this target, then draws into the window
    /// again.
    pub fn draw_into(&self, draw: impl FnOnce()) {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above, and BindFramebuffer reads
        // only the plain values passed to it.
        unsafe { gl::BindFramebuffer(gl::FRAMEBUFFER, self.framebuffer) };
        draw();
        // SAFETY: As above.
        unsafe { gl::BindFramebuffer(gl::FRAMEBUFFER, 0) };
    }

    /// The texture that the draws of `draw_into` went into.
    pub fn texture(&self) -> &Texture<'a> {
        &self.texture
    }
}

impl Drop for RenderTarget<'_> {
    fn drop(&mut self) {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above. DeleteFramebuffers reads
        // one name from the field framebuffer.
        unsafe { gl::DeleteFramebuffers(1, &raw const self.framebuffer) };
    }
}
