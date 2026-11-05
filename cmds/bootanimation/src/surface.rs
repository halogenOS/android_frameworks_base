// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The layer of the animation, created by `surface/boot_surface.cpp`. Creating a top-level layer
//! needs a transaction written only by libgui, so this one call is in C++.

use std::num::NonZeroU16;
use std::ptr::{self, NonNull};

use boot_surface_bindgen::{
    boot_surface_create, boot_surface_destroy, boot_surface_size, boot_surface_window,
    ANativeWindow, BootSurface, BootSurfaceSize,
};

use crate::error::Error;

/// The size of the window of the layer in pixels.
#[derive(Clone, Copy, Debug)]
pub struct Size {
    pub width: NonZeroU16,
    pub height: NonZeroU16,
}

/// The layer as `boot_surface_create` returned it. Dropping it removes the layer.
struct Layer(NonNull<BootSurface>);

impl Drop for Layer {
    fn drop(&mut self) {
        // SAFETY: self.0 came from boot_surface_create, and Drop runs once, so it is released once
        // and not used afterwards.
        unsafe { boot_surface_destroy(self.0.as_ptr()) };
    }
}

/// The layer of the animation with its window. Dropping it removes the layer.
pub struct Surface {
    window: NonNull<ANativeWindow>,
    size: Size,
    /// Removes the layer when the Surface drops. Nothing reads it.
    _layer: Layer,
}

impl Surface {
    /// Waits for `SurfaceFlinger` and creates the layer. Fails when the layer has no window, or a
    /// side of its window is outside 1 to `u16::MAX` pixels.
    pub fn create() -> Result<Self, Error> {
        let mut out = ptr::null_mut();
        // SAFETY: out is a valid pointer to a local, and boot_surface_create writes it only when it
        // returns 0.
        let status = unsafe { boot_surface_create(&raw mut out) };
        if status != 0 {
            return Err(Error::Layer { status });
        }
        let layer = Layer(NonNull::new(out).ok_or(Error::MissingLayer)?);
        // SAFETY: layer came from boot_surface_create and is released only when it drops.
        let window = unsafe { boot_surface_window(layer.0.as_ptr()) };
        let window = NonNull::new(window).ok_or(Error::MissingWindow)?;
        // SAFETY: As above.
        let BootSurfaceSize { width, height } = unsafe { boot_surface_size(layer.0.as_ptr()) };
        let window_side = |pixels: i32| u16::try_from(pixels).ok().and_then(NonZeroU16::new);
        let size = match (window_side(width), window_side(height)) {
            (Some(width), Some(height)) => Size { width, height },
            _ => return Err(Error::WindowSize { width, height }),
        };
        Ok(Self { window, size, _layer: layer })
    }

    /// The window of the layer. It is valid as long as this Surface.
    pub fn window(&self) -> NonNull<ANativeWindow> {
        self.window
    }

    /// The size of the window in pixels.
    pub fn size(&self) -> Size {
        self.size
    }
}
