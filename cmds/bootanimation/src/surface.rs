// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The layer of the animation, created by `surface/boot_surface.cpp`. Creating a top-level layer
//! needs a transaction written only by libgui, so this one call is in C++.

use std::ptr::{self, NonNull};

use boot_surface_bindgen::{
    boot_surface_create, boot_surface_destroy, boot_surface_size, boot_surface_window,
    ANativeWindow, BootSurface, BootSurfaceSize,
};

use crate::error::Error;

/// The layer of the animation. Dropping it removes the layer.
pub struct Surface(NonNull<BootSurface>);

impl Surface {
    /// Waits for `SurfaceFlinger` and creates the layer.
    pub fn create() -> Result<Self, Error> {
        let mut out = ptr::null_mut();
        // SAFETY: out is a valid pointer to a local, and boot_surface_create writes it only when it
        // returns 0.
        let status = unsafe { boot_surface_create(&raw mut out) };
        match status {
            0 => NonNull::new(out).map(Self).ok_or(Error::MissingLayer),
            _ => Err(Error::Layer { status }),
        }
    }

    /// The window of the layer. It is valid as long as this Surface.
    pub fn window(&self) -> Result<NonNull<ANativeWindow>, Error> {
        // SAFETY: self.0 came from boot_surface_create and is released only by Drop.
        let window = unsafe { boot_surface_window(self.0.as_ptr()) };
        NonNull::new(window).ok_or(Error::MissingWindow)
    }

    /// The width and height of the window in pixels. Fails on a side outside 0 to 65535 pixels.
    pub fn size(&self) -> Result<(u16, u16), Error> {
        // SAFETY: self.0 came from boot_surface_create and is released only by Drop.
        let BootSurfaceSize { width, height } = unsafe { boot_surface_size(self.0.as_ptr()) };
        let side = |side: i32| {
            u16::try_from(side).map_err(|cause| Error::WindowSize { width, height, cause })
        };
        Ok((side(width)?, side(height)?))
    }
}

impl Drop for Surface {
    fn drop(&mut self) {
        // SAFETY: self.0 came from boot_surface_create, and Drop runs once, so it is released once
        // and not used afterwards.
        unsafe { boot_surface_destroy(self.0.as_ptr()) };
    }
}
