// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! What the system reports to the animation in system properties. `odrefresh` reports how far it
//! has compiled the system in service.bootanim.progress before `system_server` starts, when it has
//! to compile. `system_server` reports the stage of the boot in service.bootanim.stage and the
//! progress of the app update in service.bootanim.step, and saves how long each stage took in
//! `persist.bootanim.stage_times` when the boot completes. `SurfaceFlinger` sets
//! service.bootanim.exit and service.bootanim.progress to 0 when it starts the animation, and
//! `WindowManager` sets service.bootanim.exit to 1 once the system can show its first window. This
//! module is the only reader of these properties.

use std::sync::mpsc::{self, Receiver, TryRecvError};

use crate::boot_progress::StageTimes;
use crate::boot_stage::{BootStage, CompileProgress, Step};
use crate::error::Error;
use crate::properties;

const COMPILE_PROPERTY: &str = "service.bootanim.progress";
const STAGE_PROPERTY: &str = "service.bootanim.stage";
const STEP_PROPERTY: &str = "service.bootanim.step";
const EXIT_PROPERTY: &str = "service.bootanim.exit";
const STAGE_TIMES_PROPERTY: &str = "persist.bootanim.stage_times";

/// The reports of the system up to a frame.
#[derive(Clone, Copy, Debug, Default)]
pub struct BootReport {
    /// The last reported progress of the compile.
    compile: Option<CompileProgress>,
    /// The last reported stage that this binary knows, or None before the first one.
    stage: Option<BootStage>,
    /// The last reported progress of the app update.
    step: Option<Step>,
    /// Whether the system requested the exit of the animation.
    exit_requested: bool,
}

impl BootReport {
    /// The progress of the compile while the boot is in it, or None before the first compilation
    /// is done and from the first reported stage on. A progress of 0 is that of no compilation:
    /// `SurfaceFlinger` sets it when it starts the animation, and `odrefresh` before it compiles.
    pub fn compile(self) -> Option<CompileProgress> {
        self.compile.filter(|compile| compile.percent() > 0 && self.stage.is_none())
    }

    /// The last reported stage that this binary knows, or None before the first one. A stage that
    /// this binary does not know leaves the last known one.
    pub fn stage(self) -> Option<BootStage> {
        self.stage
    }

    /// The progress of the app update while the boot is in it, or None outside of it and before
    /// its first report.
    pub fn step(self) -> Option<Step> {
        self.step.filter(|_| self.stage == Some(BootStage::AppUpdate))
    }

    /// Whether the system requested the exit of the animation.
    pub fn exit_requested(self) -> bool {
        self.exit_requested
    }
}

/// A new value of one of the watched properties.
enum Change {
    Compile(CompileProgress),
    Stage(BootStage),
    Step(Step),
    /// service.bootanim.exit, which requests the exit when it is not 0.
    Exit(i32),
}

/// The properties that the system reports in, each watched on a thread of its own.
pub struct BootReports {
    changes: Receiver<Result<Change, Error>>,
    latest: BootReport,
}

impl BootReports {
    /// Starts to watch the reports of the system. Fails when a watcher cannot start.
    pub fn watch() -> Result<Self, Error> {
        // Each watcher waits until the frame loop takes its value, so it reads the next change only
        // after that.
        let (sender, changes) = mpsc::sync_channel(0);
        properties::watch(COMPILE_PROPERTY, sender.clone(), Change::Compile)?;
        properties::watch(STAGE_PROPERTY, sender.clone(), Change::Stage)?;
        properties::watch(STEP_PROPERTY, sender.clone(), Change::Step)?;
        properties::watch(EXIT_PROPERTY, sender, Change::Exit)?;
        Ok(Self { changes, latest: BootReport::default() })
    }

    /// The reports up to now: the last ones, with every change since then applied in order.
    /// Fails when a property could not be watched.
    pub fn latest(&mut self) -> Result<BootReport, Error> {
        loop {
            match self.changes.try_recv() {
                Ok(change) => match change? {
                    Change::Compile(compile) => self.latest.compile = Some(compile),
                    Change::Stage(stage) => self.latest.stage = Some(stage),
                    Change::Step(step) => self.latest.step = Some(step),
                    Change::Exit(exit) => self.latest.exit_requested = exit != 0,
                },
                // Disconnected: every watcher has ended after it sent its failure.
                Err(TryRecvError::Empty | TryRecvError::Disconnected) => return Ok(self.latest),
            }
        }
    }
}

/// The times that the last boot saved, or None when no boot saved any. Entries that this binary
/// does not recognize are logged and left out, so the other entries still count.
pub fn saved_stage_times() -> Result<Option<StageTimes>, Error> {
    Ok(properties::text(STAGE_TIMES_PROPERTY)?.map(|value| {
        let (times, unknown) = StageTimes::parse(&value);
        for entry in unknown {
            log::warn!("{STAGE_TIMES_PROPERTY} has the entry {entry:?}, which is unknown here");
        }
        times
    }))
}
