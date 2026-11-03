// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The layer of the animation, created by `surface/boot_surface.cpp`. Creating a top-level layer
//! needs a transaction written only by libgui, so this one call is in C++.

use std::ptr::{self, NonNull};

use boot_surface_bindgen::{
    boot_surface_create, boot_surface_destroy, boot_surface_size, boot_surface_window,
    ANativeWindow, BootSurface,
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
            0 => {
                Ok(Self(NonNull::new(out).expect("boot_surface_create returned 0 without a layer")))
            }
            _ => Err(Error::Layer { status }),
        }
    }

    /// The window of the layer. It is valid as long as this Surface.
    pub fn window(&self) -> NonNull<ANativeWindow> {
        // SAFETY: self.0 came from boot_surface_create and is released only by Drop.
        let window = unsafe { boot_surface_window(self.0.as_ptr()) };
        NonNull::new(window).expect("boot_surface_create returned a layer without a window")
    }

    /// The width and height of the window in pixels.
    pub fn size(&self) -> (i32, i32) {
        // SAFETY: self.0 came from boot_surface_create and is released only by Drop.
        let size = unsafe { boot_surface_size(self.0.as_ptr()) };
        (size.width, size.height)
    }
}

impl Drop for Surface {
    fn drop(&mut self) {
        // SAFETY: self.0 came from boot_surface_create, and Drop runs once, so it is released once
        // and not used afterwards.
        unsafe { boot_surface_destroy(self.0.as_ptr()) };
    }
}
