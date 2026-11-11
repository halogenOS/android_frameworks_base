// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! When the ending of the animation plays, and when the process ends. The ending starts early
//! enough to have played when the system is expected to request the exit, by the stage times
//! recorded in the last boot. The scene then leaves while the system draws its interface below
//! it. When the request comes before the ending has played, the rest of the ending plays sped up
//! in a short tail. Once a frame has shown the whole ending, the frame loop waits for the request.
//! The process ends once the system has requested the exit and a frame has shown the whole
//! ending. A request after the ending has played ends it at once.

use std::time::Duration;

use crate::boot_progress::ExitRequest;

/// How long the rest of the ending plays after the exit request. The system accepts input only
/// once the process has ended. A product choice.
const TAIL: Duration = Duration::from_millis(300);

/// How the ending moves on.
#[derive(Clone, Copy, Debug)]
enum Pace {
    /// Not started.
    Waiting,
    /// At its planned pace from start on.
    Planned { start: Duration },
    /// Sped up so that the rest of it, from played on, plays in `TAIL` after the exit request at
    /// requested.
    Hurried { requested: Duration, played: Duration },
}

/// What the frame loop does at a frame.
#[derive(Clone, Copy, Debug)]
pub enum FrameAction {
    /// Draws the frame with the ending played this far, or None before it starts.
    Draw(Option<Duration>),
    /// Leaves the last frame, which has shown the whole ending, on the screen until the system
    /// requests the exit.
    WaitForExitRequest,
    EndProcess,
}

/// The ending of the animation, from frame to frame. Times count from the start of the animation.
pub struct Ending {
    /// How long the ending takes at its planned pace.
    length: Duration,
    pace: Pace,
    /// How far the last frame showed the ending, or None before it started.
    shown: Option<Duration>,
}

impl Ending {
    /// An ending that takes length at its planned pace.
    pub fn new(length: Duration) -> Self {
        Self { length, pace: Pace::Waiting, shown: None }
    }

    /// What the frame loop does at elapsed, with request as the system has made or is expected to
    /// make it.
    pub fn at(&mut self, elapsed: Duration, request: ExitRequest) -> FrameAction {
        self.pace = match (self.pace, request) {
            (Pace::Waiting | Pace::Planned { .. }, ExitRequest::Made) => {
                log::info!("exit requested after {elapsed:?}");
                Pace::Hurried { requested: elapsed, played: self.played(elapsed) }
            }
            (Pace::Waiting, ExitRequest::ExpectedIn(time)) if time <= self.length => {
                Pace::Planned { start: elapsed }
            }
            (pace, _) => pace,
        };
        if self.shown == Some(self.length) {
            return match self.pace {
                Pace::Hurried { .. } => FrameAction::EndProcess,
                Pace::Waiting | Pace::Planned { .. } => FrameAction::WaitForExitRequest,
            };
        }
        self.shown = match self.pace {
            Pace::Waiting => None,
            Pace::Planned { .. } | Pace::Hurried { .. } => Some(self.played(elapsed)),
        };
        FrameAction::Draw(self.shown)
    }

    /// How far the ending has played at elapsed, zero before it starts.
    fn played(&self, elapsed: Duration) -> Duration {
        match self.pace {
            Pace::Waiting => Duration::ZERO,
            Pace::Planned { start } => elapsed.saturating_sub(start).min(self.length),
            Pace::Hurried { requested, played } => {
                let tail_part = elapsed.saturating_sub(requested).div_duration_f32(TAIL);
                let rest = self.length.saturating_sub(played);
                // A rest scaled by 0.0 is zero. The tail then ends at the whole length exactly.
                self.length.saturating_sub(rest.mul_f32(1.0 - tail_part.min(1.0)))
            }
        }
    }
}
