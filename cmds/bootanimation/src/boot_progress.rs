// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! How far the bar is through the boot. The bar has a part for the time until `system_server`
//! reports its first stage, a part for the compile of the system by `odrefresh` when it compiles
//! in that time, then a part for each stage. Each time the boot reaches a part, the rest of the
//! bar is shared by that part and the parts still to come by how long each took on the last boot,
//! so the bar moves at an even pace through a boot that goes like the last one and never jumps
//! when one goes differently. Inside a part the bar eases towards the end of the part
//! without reaching it before the part ends, and during the compile and while the apps are
//! updated, it follows the reported progress of each.

mod stage_times;

use std::f32::consts::PI;
use std::iter;
use std::time::{Duration, Instant};

pub use self::stage_times::StageTimes;
use crate::boot_report::BootReport;
use crate::boot_stage::{BootStage, CompileProgress, Step};
use crate::progress::Progress;

/// The part of the time a part is expected to take through which the bar moves at an even pace.
/// After it, the bar slows down, so it reaches the end of the part only when the part ends, also
/// when the part takes longer than expected. A product choice for how the bar looks.
const EVEN_PART: f32 = 0.85;
/// How far the pace of the bar sways inside a part. A product choice for how the bar looks.
const SWAY: f32 = 0.55;
/// How fast the pace of the bar sways, in radians per part: twice per part.
const SWAY_FREQUENCY: f32 = 4.0 * PI;

/// A part of the progress bar.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Part {
    /// From the start of the animation until `system_server` reports its first stage, except for
    /// the compile.
    Start,
    /// While `odrefresh` compiles the system, until `system_server` reports its first stage.
    Compile,
    Stage(BootStage),
}

impl Part {
    /// The parts after this one, in the order of the boot.
    fn later(self) -> impl Iterator<Item = Self> {
        let next = |part: &Self| match part {
            Self::Start => Some(Self::Compile),
            Self::Compile => Some(Self::Stage(BootStage::FIRST)),
            Self::Stage(stage) => stage.next().map(Self::Stage),
        };
        iter::successors(next(&self), next)
    }

    /// Whether every boot goes through this part. The system is compiled only after an update of
    /// ART or when its compiled files are missing.
    fn on_every_boot(self) -> bool {
        match self {
            Self::Start => true,
            Self::Compile => false,
            Self::Stage(stage) => stage.on_every_boot(),
        }
    }

    /// How long the part takes when the last boot did not save how long it took: the times of a
    /// typical boot. The `system_server` and packages stages share one time evenly. The compile
    /// gets a time longer than the rest of the boot, so it fills most of the bar.
    fn built_in_time(self) -> Duration {
        Duration::from_millis(match self {
            Self::Start => 5000,
            Self::Compile => 60000,
            Self::Stage(BootStage::SystemServer | BootStage::Packages) => 1600,
            Self::Stage(BootStage::AppUpdate) => 5200,
            Self::Stage(BootStage::Services) => 2700,
            Self::Stage(BootStage::Interface) => 3900,
        })
    }
}

/// How long each part of the boot is expected to take.
struct ExpectedTimes {
    /// How long each part took on the last boot, or None on the first boot.
    last_boot: Option<StageTimes>,
}

impl ExpectedTimes {
    /// How long part took on the last boot, or None when the last boot saved no time for it.
    fn on_last_boot(&self, part: Part) -> Option<Duration> {
        self.last_boot.as_ref().and_then(|times| times.get(part))
    }

    /// The time of the part the boot is in, or its built-in time when the last boot saved none. A
    /// part that took no time on the last boot takes time now, so it gets its built-in time too.
    fn of_reached(&self, part: Part) -> Duration {
        self.on_last_boot(part)
            .filter(|time| !time.is_zero())
            .unwrap_or_else(|| part.built_in_time())
    }

    /// The time of a part the boot has not reached yet, or its built-in time when the last boot
    /// saved none. A part that only some boots go through is not expected, so a boot without it
    /// moves evenly, and a boot with it makes room for it once it is reached.
    fn of_coming(&self, part: Part) -> Duration {
        if part.on_every_boot() {
            self.on_last_boot(part).unwrap_or_else(|| part.built_in_time())
        } else {
            Duration::ZERO
        }
    }

    /// part laid out on the bar from from, as the boot reached it at start.
    fn lay_out(&self, part: Part, from: Progress, start: Instant) -> PartOnBar {
        let expected = self.of_reached(part);
        let coming: Duration = part.later().map(|later| self.of_coming(later)).sum();
        // expected is never zero, so the share is from above 0 to 1.
        let share = expected.as_secs_f32() / (expected + coming).as_secs_f32();
        PartOnBar { part, start, expected, from, to: from.towards(Progress::COMPLETE, share) }
    }
}

/// The part the boot is in, and where it lies on the bar.
#[derive(Clone, Copy, Debug)]
struct PartOnBar {
    part: Part,
    /// When the boot reached the part.
    start: Instant,
    /// How long the part is expected to take.
    expected: Duration,
    /// Where the bar was when the boot reached the part.
    from: Progress,
    /// Where the part ends on the bar.
    to: Progress,
}

impl PartOnBar {
    /// The part of the way from `from` to `to` that the bar has moved at now, with reported as the
    /// part of the work of the part that the system reported done, for the parts it reports.
    fn done_part(&self, now: Instant, reported: Option<f32>) -> f32 {
        reported.unwrap_or_else(|| {
            let taken = now.saturating_duration_since(self.start).as_secs_f32()
                / self.expected.as_secs_f32();
            sway(slow_down(taken))
        })
    }
}

/// The part of the way through a part for taken, the part of its expected time that it has run:
/// taken itself up to `EVEN_PART`, then closer and closer to 1 without reaching it.
fn slow_down(taken: f32) -> f32 {
    if taken <= EVEN_PART {
        taken
    } else {
        // Leaves EVEN_PART at the same pace as it reaches it.
        1.0 - (1.0 - EVEN_PART) * (-(taken - EVEN_PART) / (1.0 - EVEN_PART)).exp()
    }
}

/// part with the sway of the pace: from 0 to 1 as part goes from 0 to 1, always forward.
fn sway(part: f32) -> f32 {
    part - SWAY * (SWAY_FREQUENCY * part).sin() / SWAY_FREQUENCY
}

/// How far the bar is through the boot.
pub struct BootProgress {
    expected: ExpectedTimes,
    current: PartOnBar,
    /// The furthest progress so far, below which the bar never goes.
    reached: Progress,
}

impl BootProgress {
    /// The progress of a boot whose animation started at start, with the stage times that the
    /// last boot saved, or None on the first boot.
    pub fn starting_at(start: Instant, last_boot: Option<StageTimes>) -> Self {
        let expected = ExpectedTimes { last_boot };
        let current = expected.lay_out(Part::Start, Progress::default(), start);
        Self { expected, current, reached: Progress::default() }
    }

    /// The progress at now, with the reports of the system up to now. It never moves backward.
    pub fn at(&mut self, now: Instant, report: BootReport) -> Progress {
        let reported_part =
            report.stage().map(Part::Stage).or_else(|| report.compile().map(|_| Part::Compile));
        if let Some(part) = reported_part {
            if part != self.current.part {
                self.current = self.expected.lay_out(part, self.reached, now);
            }
        }
        // The compile is reported only before the first stage, and the step only in the app update.
        let reported_done = report
            .compile()
            .map(CompileProgress::done_part)
            .or_else(|| report.step().map(Step::done_part));
        let current = self.current;
        let progress = current.from.towards(current.to, current.done_part(now, reported_done));
        self.reached = self.reached.max(progress);
        self.reached
    }
}
