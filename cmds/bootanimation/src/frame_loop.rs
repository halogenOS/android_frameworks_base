// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Draws one frame per vsync until the system has requested the exit and the ending has played.

use std::time::Instant;

use crate::bar::Bar;
use crate::boot_progress::{BootProgress, StageTimes};
use crate::boot_report::BootReports;
use crate::ending::{Ending, FrameAction};
use crate::error::Error;
use crate::renderer::Renderer;
use crate::scene::{Frame, GlassFill};

/// Draws scene until the system has requested the exit and a frame has shown the whole ending,
/// with the bar paced by the stage times recorded in the last boot and by reports. The ending
/// starts when those times expect the request in the time it takes. Each frame waits for vsync
/// before its buffer swaps.
pub fn run(
    renderer: &Renderer,
    scene: &mut GlassFill<'_>,
    mut reports: BootReports,
    last_boot: Option<StageTimes>,
) -> Result<(), Error> {
    let start = Instant::now();
    let mut boot = BootProgress::starting_at(start, last_boot);
    let mut bar = Bar::new(GlassFill::fill_length());
    let mut ending = Ending::new(GlassFill::ending_length());
    loop {
        let now = Instant::now();
        let elapsed = now - start;
        let report = reports.latest()?;
        let boot_point = boot.at(now, report);
        let ending_played = match ending.at(elapsed, boot_point.exit_request) {
            FrameAction::Draw(played) => played,
            FrameAction::WaitForExitRequest => {
                reports.wait_for_exit_request()?;
                continue;
            }
            FrameAction::EndProcess => return Ok(()),
        };
        let progress = bar.at(elapsed, boot_point.progress, ending_played);
        let frame = Frame {
            progress,
            elapsed,
            ending_played,
            exit_requested: report.exit_requested(),
            stage: report.stage(),
            step: report.step(),
        };
        renderer.render(|| scene.draw(&frame))?;
    }
}
