// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The progress that the bar shows. It eases towards the progress reported by the boot. From the
//! start of the ending on, it fills up at an even pace. It is full when the exit starts.

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
    /// How long the bar takes to fill up from the start of the ending on.
    fill_length: Duration,
    /// Where the bar was at the first frame of the ending, or None before it.
    filled_from: Option<Progress>,
}

impl Bar {
    /// An empty bar that takes `fill_length` of the ending to fill up.
    pub fn new(fill_length: Duration) -> Self {
        Self {
            shown: Progress::default(),
            previous_frame: Duration::ZERO,
            fill_length,
            filled_from: None,
        }
    }

    /// The progress that the bar shows at elapsed since the start of the animation, with boot as
    /// the progress of the boot and `ending_played` as how far the ending has played, None
    /// before it.
    pub fn at(
        &mut self,
        elapsed: Duration,
        boot: Progress,
        ending_played: Option<Duration>,
    ) -> Progress {
        self.shown = match ending_played {
            None => {
                self.shown.towards(boot, easing_part(elapsed.saturating_sub(self.previous_frame)))
            }
            Some(played) => {
                let from = *self.filled_from.get_or_insert(self.shown);
                from.towards(Progress::COMPLETE, played.div_duration_f32(self.fill_length).min(1.0))
            }
        };
        self.previous_frame = elapsed;
        self.shown
    }
}
