// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! How far the boot is, as a part of the whole boot.

use num_traits::ToPrimitive;

/// The part of the boot that is done, from 0.0 to 1.0.
#[derive(Clone, Copy, Debug, Default, PartialEq, PartialOrd)]
pub struct Progress(f32);

impl Progress {
    /// The whole boot done.
    pub const COMPLETE: Self = Self(1.0);

    /// The part that is done, from 0.0 to 1.0.
    pub fn fraction(self) -> f32 {
        self.0
    }

    /// The part that is done in whole percent, rounded down.
    pub fn percent(self) -> u8 {
        // towards clamps its part, so a progress lies from 0.0 to 1.0 and converts to a u8. A NaN
        // part is the one exception, and it counts as 0.
        (self.0 * 100.0).floor().to_u8().unwrap_or_default()
    }

    /// The progress moved by a part of the way to target, the part from 0.0 to 1.0. It never moves
    /// backward or past target.
    pub fn towards(self, target: Self, part: f32) -> Self {
        if target <= self {
            self
        } else {
            Self(self.0 + (target.0 - self.0) * part.clamp(0.0, 1.0))
        }
    }

    /// The further of this progress and other.
    pub fn max(self, other: Self) -> Self {
        Self(self.0.max(other.0))
    }
}
