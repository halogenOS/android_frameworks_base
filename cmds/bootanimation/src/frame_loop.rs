// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Draws one frame per vsync until the exit of the scene has played.

use std::time::Instant;

use crate::bar::Bar;
use crate::boot_progress::{BootProgress, StageTimes};
use crate::boot_report::BootReports;
use crate::error::Error;
use crate::renderer::Renderer;
use crate::scene::{Frame, GlassFill};

/// Draws scene until its exit has played after the exit request, with the bar paced by the stage
/// times of the last boot and by reports. The swap of each frame waits for vsync.
pub fn run(
    renderer: &Renderer,
    scene: &mut GlassFill<'_>,
    mut reports: BootReports,
    last_boot: Option<StageTimes>,
) -> Result<(), Error> {
    let start = Instant::now();
    let mut boot = BootProgress::starting_at(start, last_boot);
    let mut bar = Bar::new(GlassFill::fill_length());
    let mut ready = None;
    loop {
        let now = Instant::now();
        let elapsed = now - start;
        let report = reports.latest()?;
        if ready.is_none() && report.exit_requested() {
            log::info!("exit requested after {elapsed:?}");
            ready = Some(elapsed);
        }
        if ready.is_some_and(|ready| elapsed >= ready + GlassFill::exit_length()) {
            return Ok(());
        }
        let progress = bar.at(elapsed, boot.at(now, report), ready);
        let frame = Frame { progress, elapsed, ready, stage: report.stage(), step: report.step() };
        renderer.render(|| scene.draw(&frame))?;
    }
}
