/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "BootAnimation"

#include "boot_surface.h"

#include <inttypes.h>

#include <cstdlib>
#include <memory>
#include <optional>
#include <string>
#include <vector>

#include <android-base/properties.h>
#include <android/gui/ISurfaceComposerClient.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <ui/DisplayMode.h>
#include <ui/LayerStack.h>
#include <ui/PixelFormat.h>
#include <ui/Rect.h>
#include <ui/Rotation.h>
#include <ui/Size.h>
#include <utils/Log.h>

// The members are released in reverse order, so the layer goes before the connection that
// created it. The layer is removed when the last reference to it is released.
struct BootSurface {
    android::sp<android::SurfaceComposerClient> session;
    android::sp<android::IBinder::DeathRecipient> composerDeath;
    android::sp<android::SurfaceControl> layer;
    android::sp<android::Surface> window;
    BootSurfaceSize size;
};

namespace android {
namespace {

constexpr int32_t kAboveEverything = 0x40000000;

// Ends the process when SurfaceFlinger dies. Rendering can wait for a buffer that SurfaceFlinger
// never releases, so the process exits at once and never returns into Rust.
class ExitOnComposerDeath final : public IBinder::DeathRecipient {
    void binderDied(const wp<IBinder>&) override {
        ALOGE("SurfaceFlinger died, so the boot animation exits");
        std::_Exit(EXIT_FAILURE);
    }
};

std::optional<ui::Rotation> parseOrientation(const std::string& value) {
    if (value == "ORIENTATION_0") return ui::ROTATION_0;
    if (value == "ORIENTATION_90") return ui::ROTATION_90;
    if (value == "ORIENTATION_180") return ui::ROTATION_180;
    if (value == "ORIENTATION_270") return ui::ROTATION_270;
    return std::nullopt;
}

// The rotation away from the natural orientation of the display, set for devices whose natural
// orientation differs from the orientation of the animation, such as tablets that are portrait
// for app compatibility.
ui::Rotation readOrientation(PhysicalDisplayId displayId) {
    std::string value =
            base::GetProperty("ro.bootanim.set_orientation_" + std::to_string(displayId.value), "");
    if (value.empty()) {
        value = base::GetProperty("ro.bootanim.set_orientation_logical_0", "");
    }
    if (value.empty()) {
        return ui::ROTATION_0;
    }
    const std::optional<ui::Rotation> orientation = parseOrientation(value);
    if (!orientation) {
        ALOGW("Unknown orientation %s, so the animation is not rotated", value.c_str());
        return ui::ROTATION_0;
    }
    return *orientation;
}

status_t createBootSurface(std::unique_ptr<BootSurface>& out) {
    // Binder threads deliver the death of SurfaceFlinger and the buffer releases.
    ProcessState::self()->startThreadPool();
    if (defaultServiceManager()->waitForService(String16("SurfaceFlinger")) == nullptr) {
        ALOGE("Could not wait for SurfaceFlinger");
        return NAME_NOT_FOUND;
    }

    const std::vector<PhysicalDisplayId> displayIds = SurfaceComposerClient::getPhysicalDisplayIds();
    if (displayIds.empty()) {
        ALOGE("Could not find a physical display");
        return NAME_NOT_FOUND;
    }
    const PhysicalDisplayId displayId = displayIds.front();
    const sp<IBinder> display = SurfaceComposerClient::getPhysicalDisplayToken(displayId);
    if (display == nullptr) {
        ALOGE("Could not get the token for display %" PRIu64, displayId.value);
        return NAME_NOT_FOUND;
    }
    ui::DisplayMode mode;
    if (const status_t error = SurfaceComposerClient::getActiveDisplayMode(display, &mode);
        error != NO_ERROR) {
        ALOGE("Could not read the active mode for the display: %d", error);
        return error;
    }

    auto surface = std::make_unique<BootSurface>();
    surface->session = sp<SurfaceComposerClient>::make();
    if (const status_t error = surface->session->initCheck(); error != NO_ERROR) {
        ALOGE("Could not connect to SurfaceFlinger: %d", error);
        return error;
    }
    surface->composerDeath = sp<ExitOnComposerDeath>::make();
    if (const status_t error = surface->session->linkToComposerDeath(surface->composerDeath);
        error != NO_ERROR) {
        ALOGE("Could not watch for the death of SurfaceFlinger: %d", error);
        return error;
    }

    const ui::Rotation orientation = readOrientation(displayId);
    ui::Size size = mode.resolution;
    size.rotate(orientation);
    surface->layer = surface->session->createSurface(String8("BootAnimation"), size.width,
                                                     size.height, PIXEL_FORMAT_RGBX_8888,
                                                     gui::ISurfaceComposerClient::eOpaque);
    if (surface->layer == nullptr) {
        ALOGE("SurfaceFlinger did not create the layer");
        return NO_INIT;
    }
    const ui::LayerStack layerStack = ui::LayerStack::fromValue(0);
    SurfaceComposerClient::Transaction transaction;
    transaction.setDisplayLayerStack(display, layerStack);
    // A display in its natural orientation keeps the projection set by SurfaceFlinger.
    if (orientation != ui::ROTATION_0) {
        const Rect bounds(size.width, size.height);
        transaction.setDisplayProjection(display, orientation, bounds, bounds);
    }
    if (const status_t error = transaction.setLayerStack(surface->layer, layerStack)
                                       .setLayer(surface->layer, kAboveEverything)
                                       .apply();
        error != NO_ERROR) {
        ALOGE("SurfaceFlinger did not show the layer: %d", error);
        return error;
    }
    surface->window = surface->layer->getSurface();
    if (surface->window == nullptr) {
        ALOGE("SurfaceFlinger did not create the window of the layer");
        return NO_INIT;
    }
    surface->size = {.width = size.width, .height = size.height};
    out = std::move(surface);
    return NO_ERROR;
}

} // namespace
} // namespace android

int32_t boot_surface_create(BootSurface** out) {
    std::unique_ptr<BootSurface> surface;
    const android::status_t status = android::createBootSurface(surface);
    if (status == android::NO_ERROR) {
        *out = surface.release();
    }
    return status;
}

ANativeWindow* boot_surface_window(const BootSurface* surface) {
    return surface->window.get();
}

BootSurfaceSize boot_surface_size(const BootSurface* surface) {
    return surface->size;
}

void boot_surface_destroy(BootSurface* surface) {
    delete surface;
}
