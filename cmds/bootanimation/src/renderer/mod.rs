// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! EGL and OpenGL ES 3.0 on the window of the layer. Scenes build their programs, meshes and
//! textures with the `Renderer`. These borrow it, and its context is current on its thread from
//! `Renderer::new` until it drops, so they cannot outlive the context. The `gl` crate exposes
//! OpenGL ES as raw bindings only, so every call into it is unsafe.

mod mesh;
mod program;
mod render_target;
mod text;
mod texture;

use std::ffi::c_void;
use std::ptr;

use khronos_egl as egl;

pub use self::mesh::Mesh;
pub use self::program::{Blending, Program, ShaderConstant, Stage, Uniform};
pub use self::render_target::RenderTarget;
pub use self::text::{Align, Font, Label, RasterizeError, Style, TextProgram};
pub use self::texture::Texture;
use crate::error::Error;
use crate::surface::Surface;

const CONFIG: [egl::Int; 11] = [
    egl::RED_SIZE,
    8,
    egl::GREEN_SIZE,
    8,
    egl::BLUE_SIZE,
    8,
    egl::RENDERABLE_TYPE,
    egl::OPENGL_ES3_BIT,
    egl::SURFACE_TYPE,
    egl::WINDOW_BIT,
    egl::NONE,
];
const CONTEXT: [egl::Int; 3] = [egl::CONTEXT_CLIENT_VERSION, 3, egl::NONE];

/// The size of the window, in pixels.
#[derive(Clone, Copy, Debug)]
pub struct Window {
    pub width: f32,
    pub height: f32,
}

impl Window {
    /// This window measured in a unit of which it is height high.
    pub fn scaled_to_height(self, height: f32) -> Self {
        Self { width: self.width * height / self.height, height }
    }
}

/// A place on the window, in pixels from its top left corner, y down.
#[derive(Clone, Copy, Debug)]
pub struct Pixels {
    pub x: f32,
    pub y: f32,
}

/// Draws scenes into the window of the layer. Dropping it releases EGL, then the layer.
pub struct Renderer {
    egl: egl::DynamicInstance<egl::EGL1_4>,
    display: egl::Display,
    context: egl::Context,
    surface: egl::Surface,
    // Fields drop after Drop::drop, so the layer outlives the EGL surface on its window.
    layer: Surface,
}

impl Renderer {
    /// Makes an OpenGL ES 3.0 context current on the window of layer, with one swap per vsync.
    pub fn new(layer: Surface) -> Result<Self, Error> {
        let window = layer.window()?;
        // SAFETY: load_required is unsafe because loading a library runs its initializers.
        // libEGL.so is the EGL of the system, and its initializers need nothing from the caller.
        let egl = unsafe { egl::DynamicInstance::<egl::EGL1_4>::load_required() }
            .map_err(Error::EglLoad)?;
        // SAFETY: get_display is unsafe because EGL reads the native display id it is given.
        // DEFAULT_DISPLAY is the id that every EGL accepts and reads nothing through.
        let display = unsafe { egl.get_display(egl::DEFAULT_DISPLAY) }.ok_or(Error::Display)?;
        let (major, minor) = egl.initialize(display).map_err(Error::Initialize)?;
        log::info!("EGL {major}.{minor}");
        let config = egl
            .choose_first_config(display, &CONFIG)
            .map_err(Error::ChooseConfig)?
            .ok_or(Error::NoConfig)?;
        let context =
            egl.create_context(display, config, None, &CONTEXT).map_err(Error::Context)?;
        // SAFETY: create_window_surface is unsafe because EGL draws into the native window it is
        // given. The window belongs to layer, which this Renderer owns, and Drop destroys the
        // EGL surface before the layer is released.
        let surface =
            unsafe { egl.create_window_surface(display, config, window.as_ptr().cast(), None) }
                .map_err(Error::WindowSurface)?;
        let renderer = Self { egl, display, context, surface, layer };
        renderer
            .egl
            .make_current(display, Some(surface), Some(surface), Some(context))
            .map_err(Error::MakeCurrent)?;
        renderer.egl.swap_interval(display, 1).map_err(Error::SwapInterval)?;
        gl::load_with(|name| {
            renderer
                .egl
                .get_proc_address(name)
                .map_or(ptr::null(), |function| function as *const c_void)
        });
        Ok(renderer)
    }

    /// The size of the window in pixels.
    pub fn window(&self) -> Result<Window, Error> {
        let (width, height) = self.size()?;
        Ok(Window { width: f32::from(width), height: f32::from(height) })
    }

    /// The width and height of the window in pixels.
    fn size(&self) -> Result<(u16, u16), Error> {
        self.layer.size()
    }

    /// Calls draw, then shows the frame at the next vsync.
    pub fn render(&self, draw: impl FnOnce() -> Result<(), Error>) -> Result<(), Error> {
        draw()?;
        self.egl.swap_buffers(self.display, self.surface).map_err(Error::Swap)
    }

    /// Panics unless the context of this renderer is current on the calling thread, the thread
    /// whose OpenGL ES calls go to it.
    fn assert_current(&self) {
        assert_eq!(
            self.egl.get_current_context(),
            Some(self.context),
            "OpenGL ES call outside the thread of the renderer"
        );
    }
}

impl Drop for Renderer {
    fn drop(&mut self) {
        let log_failure = |operation: &str, result: Result<(), egl::Error>| {
            if let Err(error) = result {
                log::error!("could not {operation}: {error}");
            }
        };
        log_failure(
            "release the current OpenGL ES context",
            self.egl.make_current(self.display, None, None, None),
        );
        log_failure(
            "destroy the EGL window surface",
            self.egl.destroy_surface(self.display, self.surface),
        );
        log_failure(
            "destroy the OpenGL ES context",
            self.egl.destroy_context(self.display, self.context),
        );
        log_failure("terminate the EGL display", self.egl.terminate(self.display));
        log_failure("release the EGL thread", self.egl.release_thread());
    }
}
