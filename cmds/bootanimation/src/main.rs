// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The boot animation. It draws on its own layer above everything else from the start of
//! `SurfaceFlinger` until `WindowManager` sets service.bootanim.exit.

mod bar;
mod boot_progress;
mod boot_report;
mod boot_stage;
mod error;
mod frame_loop;
mod progress;
mod properties;
mod renderer;
mod scene;
mod surface;

use crate::boot_report::BootReports;
use crate::error::Error;
use crate::renderer::Renderer;
use crate::scene::GlassFill;
use crate::surface::Surface;

/// The value of `ANDROID_PRIORITY_DISPLAY` defined in `system/thread_defs.h`.
const DISPLAY_PRIORITY: libc::c_int = -4;

fn main() {
    // init returns false only when logging is initialized already, and main is its only caller.
    let _newly_initialized = logger::init(
        logger::Config::default()
            .with_tag_on_device("BootAnimation")
            .with_max_level(log::LevelFilter::Info),
    );
    if let Err(error) = run() {
        log::error!("{}", error.with_causes());
        std::process::exit(1);
    }
}

fn run() -> Result<(), Error> {
    if disabled()? {
        log::info!("boot animation disabled");
        return Ok(());
    }
    // SAFETY: setpriority reads only the integer values passed to it.
    if unsafe { libc::setpriority(libc::PRIO_PROCESS, 0, DISPLAY_PRIORITY) } != 0 {
        log::warn!("could not set the display priority: {}", std::io::Error::last_os_error());
    }
    let reports = BootReports::watch();
    let last_boot = boot_report::saved_stage_times()?;
    let renderer = Renderer::new(Surface::create()?)?;
    let mut scene = GlassFill::new(&renderer)?;
    frame_loop::run(&renderer, &mut scene, reports, last_boot)
}

/// The animation is off when debug.sf.nobootanimation is set, and on a quiescent boot unless
/// ro.bootanim.quiescent.enabled allows it.
fn disabled() -> Result<bool, Error> {
    let positive = |name| Ok::<_, Error>(properties::int(name)?.is_some_and(|value| value > 0));
    Ok(positive("debug.sf.nobootanimation")?
        || (positive("ro.boot.quiescent")?
            && !properties::flag("ro.bootanim.quiescent.enabled", false)?))
}
