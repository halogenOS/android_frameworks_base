// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! How long each part of the bar took on the last boot, as `system_server` saves it in
//! `persist.bootanim.stage_times` when the boot completes: entries of a key and a time in tenths
//! of a second, separated by commas, such as `start=50,system_server=16,services=27`. The key of
//! the part before the first stage is `start`, that of the compile in that time is `compile`, and
//! the others are those of service.bootanim.stage. A part that the boot went without has no entry. `system_server` writes the property in
//! `services/core/java/com/android/server/BootAnimationReporter.java`.

use std::str::FromStr;
use std::time::Duration;

use super::Part;
use crate::boot_stage::{BootStage, UnknownStage};

/// The unit of the times in the property.
const TENTH_OF_SECOND: Duration = Duration::from_millis(100);

/// How long each part took on the last boot.
pub struct StageTimes(Vec<(Part, Duration)>);

impl StageTimes {
    /// The entries of value, and the entries left out of them because their key is unknown or
    /// their time is malformed, so the other entries still count.
    pub fn parse(value: &str) -> (Self, Vec<&str>) {
        let mut times = Vec::new();
        let mut unknown = Vec::new();
        for text in value.split(',') {
            match entry(text) {
                Some(entry) => times.push(entry),
                None => unknown.push(text),
            }
        }
        (Self(times), unknown)
    }

    /// How long part took, or None when the last boot saved no time for it. The first entry of a
    /// part counts.
    pub(super) fn get(&self, part: Part) -> Option<Duration> {
        self.0.iter().find(|(entry, _)| *entry == part).map(|(_, time)| *time)
    }
}

/// The part and the time of the entry in text, or None when it is malformed or its key is unknown.
fn entry(text: &str) -> Option<(Part, Duration)> {
    let (key, tenths) = text.split_once('=')?;
    Some((key.parse().ok()?, TENTH_OF_SECOND * tenths.parse::<u32>().ok()?))
}

impl FromStr for Part {
    type Err = UnknownStage;

    fn from_str(key: &str) -> Result<Self, Self::Err> {
        match key {
            "start" => Ok(Self::Start),
            "compile" => Ok(Self::Compile),
            stage => stage.parse::<BootStage>().map(Self::Stage),
        }
    }
}
