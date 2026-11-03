/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct ANativeWindow;

/** The layer of the boot animation, with the connection to SurfaceFlinger that created it. */
struct BootSurface;

/** The size of the window of the layer in pixels. */
struct BootSurfaceSize {
    int32_t width;
    int32_t height;
};

/**
 * Waits for SurfaceFlinger, then creates the layer of the boot animation above every other layer
 * in layer stack 0, on the first physical display. The process exits when SurfaceFlinger dies.
 *
 * Returns 0 and sets *out to a layer that the caller releases with boot_surface_destroy, or
 * returns a negative status_t and leaves *out unchanged.
 */
int32_t boot_surface_create(struct BootSurface** out);

/** The window of surface. It is valid until boot_surface_destroy releases surface. */
struct ANativeWindow* boot_surface_window(const struct BootSurface* surface);

/** The size of the window of surface. */
struct BootSurfaceSize boot_surface_size(const struct BootSurface* surface);

/** Releases surface, which removes the layer. surface must not be used afterwards. */
void boot_surface_destroy(struct BootSurface* surface);

#ifdef __cplusplus
}
#endif
