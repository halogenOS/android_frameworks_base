// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! How far the boot is, as a part of the whole boot.

use num_traits::ToPrimitive;

use crate::error::Error;

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
    pub fn percent(self) -> Result<u8, Error> {
        (self.0 * 100.0).floor().to_u8().ok_or(Error::ProgressPercent { fraction: self.0 })
    }

    /// The progress moved by a part of the way to target. It never moves backward.
    pub fn towards(self, target: Self, part: f32) -> Self {
        if target <= self {
            self
        } else {
            Self(self.0 + (target.0 - self.0) * part)
        }
    }

    /// The further of this progress and other.
    pub fn max(self, other: Self) -> Self {
        Self(self.0.max(other.0))
    }
}
