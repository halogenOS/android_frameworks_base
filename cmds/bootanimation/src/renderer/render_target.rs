// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Textures of OpenGL ES 3.0 that programs draw into, for later draws to sample.

use gl::types::{GLenum, GLuint};

use super::texture::{DrawableFormat, Texture};
use super::{gl_count, GlCount, Renderer};
use crate::error::Error;

/// Textures as large as the window and the framebuffer that draws into them. The output of the
/// fragment shader at location i goes into the texture at index i. It borrows the `Renderer`, so
/// it cannot outlive the context, and dropping it deletes the framebuffer and the textures.
pub struct RenderTarget<'a> {
    framebuffer: GLuint,
    textures: Vec<Texture<'a>>,
    renderer: &'a Renderer,
}

impl Renderer {
    /// A render target as large as the window, with a texture of each of formats, in their order.
    /// The textures are sampled with linear filtering. Fails when OpenGL ES takes no texture this
    /// large or cannot draw into them.
    pub fn window_render_target(
        &self,
        formats: &[DrawableFormat],
    ) -> Result<RenderTarget<'_>, Error> {
        let size = self.layer.size();
        let (width, height) = (usize::from(size.width.get()), usize::from(size.height.get()));
        let textures = formats
            .iter()
            .map(|&format| self.drawable_texture(width, height, format))
            .collect::<Result<Vec<_>, _>>()?;
        let attachments: Vec<GLenum> = (gl::COLOR_ATTACHMENT0..).take(textures.len()).collect();
        let count = gl_count(GlCount::DrawBuffers, attachments.len())?;
        let mut framebuffer = 0;
        // SAFETY: The context is current on this thread, asserted by drawable_texture.
        // GenFramebuffers writes one name through the pointer, which points to the local
        // framebuffer.
        unsafe { gl::GenFramebuffers(1, &raw mut framebuffer) };
        let target = RenderTarget { framebuffer, textures, renderer: self };
        // SAFETY: As above. DrawBuffers reads as many attachments from the pointer as the count
        // passed, which is the length of attachments. The other calls read only the plain values
        // passed to them.
        let status = unsafe {
            gl::BindFramebuffer(gl::FRAMEBUFFER, framebuffer);
            for (&attachment, texture) in attachments.iter().zip(&target.textures) {
                gl::FramebufferTexture2D(
                    gl::FRAMEBUFFER,
                    attachment,
                    gl::TEXTURE_2D,
                    texture.id(),
                    0,
                );
            }
            gl::DrawBuffers(count, attachments.as_ptr());
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

    /// The textures that the draws of `draw_into` went into.
    pub fn textures(&self) -> &[Texture<'a>] {
        &self.textures
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
