// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The progress that the bar shows. It eases towards the progress of the boot, and from the ready
//! report on it fills up at an even pace, so it is full when the exit starts.

use std::time::Duration;

use crate::progress::Progress;

/// The time constant of the easing of the bar: in this time, the bar moves about 63 % of the way
/// to the progress of the boot. A product choice for how the bar looks.
const EASING_TIME: Duration = Duration::from_millis(200);

/// The part of the way to the progress of the boot moved by the bar in elapsed time.
fn easing_part(elapsed: Duration) -> f32 {
    1.0 - (-elapsed.as_secs_f32() / EASING_TIME.as_secs_f32()).exp()
}

/// The bar, from frame to frame.
pub struct Bar {
    shown: Progress,
    /// The time of the previous frame since the start of the animation.
    previous_frame: Duration,
    /// How long the bar takes to fill up from the ready report on.
    fill_length: Duration,
    /// Where the bar was at the first frame after the ready report, or None before it.
    filled_from: Option<Progress>,
}

impl Bar {
    /// An empty bar that takes `fill_length` to fill up once the system reports ready.
    pub fn new(fill_length: Duration) -> Self {
        Self {
            shown: Progress::default(),
            previous_frame: Duration::ZERO,
            fill_length,
            filled_from: None,
        }
    }

    /// The progress that the bar shows at elapsed since the start of the animation, with boot as
    /// the progress of the boot and ready as the time of the ready report, None before it.
    pub fn at(&mut self, elapsed: Duration, boot: Progress, ready: Option<Duration>) -> Progress {
        self.shown = match ready {
            None => {
                self.shown.towards(boot, easing_part(elapsed.saturating_sub(self.previous_frame)))
            }
            Some(ready) => {
                let from = *self.filled_from.get_or_insert(self.shown);
                let filling = elapsed.saturating_sub(ready).as_secs_f32();
                from.towards(
                    Progress::COMPLETE,
                    (filling / self.fill_length.as_secs_f32()).min(1.0),
                )
            }
        };
        self.previous_frame = elapsed;
        self.shown
    }
}
