// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! What the animation draws.

mod glass_fill;

use std::time::Duration;

pub use glass_fill::GlassFill;

use crate::boot_stage::{BootStage, Step};
use crate::progress::Progress;

/// What a scene draws in one frame.
#[derive(Clone, Copy, Debug)]
pub struct Frame {
    /// The progress shown by the bar, eased towards the progress of the boot.
    pub progress: Progress,
    /// The time since the animation started.
    pub elapsed: Duration,
    /// The time from the start of the animation until the system reported ready by requesting
    /// the exit, or None while it has not.
    pub ready: Option<Duration>,
    /// The last reported stage of the boot that this binary knows, or None before the first one.
    pub stage: Option<BootStage>,
    /// The reported progress of the app update while the boot is in it.
    pub step: Option<Step>,
}
