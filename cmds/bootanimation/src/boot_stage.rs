// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The stage of the boot that `system_server` reports in service.bootanim.stage, the progress of
//! the app update that it reports in service.bootanim.step while it updates the apps, and the
//! progress of the compile that `odrefresh` reports in service.bootanim.progress before it.

use std::str::FromStr;
use std::{fmt, iter};

/// A stage of the boot that `system_server` reports, in the order of the boot.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BootStage {
    /// `system_server` starts.
    SystemServer,
    /// The package manager scans the installed apps.
    Packages,
    /// The apps are updated after the first boot or a system update.
    AppUpdate,
    /// The core services start.
    Services,
    /// The system interface loads until home is drawn.
    Interface,
}

impl BootStage {
    /// The stage that the boot reaches first.
    pub const FIRST: Self = Self::SystemServer;

    /// The stage that follows this one in the boot, or None after the last.
    pub fn next(self) -> Option<Self> {
        match self {
            Self::SystemServer => Some(Self::Packages),
            Self::Packages => Some(Self::AppUpdate),
            Self::AppUpdate => Some(Self::Services),
            Self::Services => Some(Self::Interface),
            Self::Interface => None,
        }
    }

    /// Every stage, in the order of the boot.
    pub fn all() -> impl Iterator<Item = Self> {
        iter::successors(Some(Self::FIRST), |stage| stage.next())
    }

    /// Whether every boot goes through this stage. The apps are updated only after the first boot
    /// or a system update.
    pub fn on_every_boot(self) -> bool {
        self != Self::AppUpdate
    }
}

/// A key of service.bootanim.stage that names no stage this binary knows.
#[derive(Debug)]
pub struct UnknownStage;

impl fmt::Display for UnknownStage {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        f.write_str("not a stage of the boot")
    }
}

impl FromStr for BootStage {
    type Err = UnknownStage;

    fn from_str(key: &str) -> Result<Self, Self::Err> {
        match key {
            "system_server" => Ok(Self::SystemServer),
            "packages" => Ok(Self::Packages),
            "app_update" => Ok(Self::AppUpdate),
            "services" => Ok(Self::Services),
            "interface" => Ok(Self::Interface),
            _ => Err(UnknownStage),
        }
    }
}

/// A percentage from 0 to `COMPLETE` that a process of the system reports in a property.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Percentage<const COMPLETE: u8> {
    percent: u8,
}

/// How far the app update is, as the percentage from 0 to 100 that `system_server` reports in
/// service.bootanim.step. It is the progress that the dexopt of ART reports to `system_server`.
pub type Step = Percentage<100>;

/// How far `odrefresh` has compiled the boot classpath and the jars of `system_server` before
/// `system_server` starts, as the percentage that it reports in service.bootanim.progress. It
/// compiles them after an update of ART or when they are missing, and reports from 0 to 90
/// (`ReportNextBootAnimationProgress` in art/odrefresh/odrefresh.cc, checked on 2026-11-04).
pub type CompileProgress = Percentage<90>;

impl<const COMPLETE: u8> Percentage<COMPLETE> {
    /// Every percentage, from 0 to `COMPLETE`.
    pub fn all() -> impl Iterator<Item = Self> {
        (0..=COMPLETE).map(|percent| Self { percent })
    }

    /// The part of the work that is done, from 0.0 to 1.0.
    pub fn done_part(self) -> f32 {
        f32::from(self.percent) / f32::from(COMPLETE)
    }

    /// How far the work is, in percent.
    pub fn percent(self) -> u8 {
        self.percent
    }
}

/// A value of a property that is not a percentage from 0 to complete.
#[derive(Debug)]
pub struct MalformedPercentage {
    complete: u8,
}

impl fmt::Display for MalformedPercentage {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "not a percentage from 0 to {}", self.complete)
    }
}

impl<const COMPLETE: u8> FromStr for Percentage<COMPLETE> {
    type Err = MalformedPercentage;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value.parse::<u8>() {
            Ok(percent) if percent <= COMPLETE => Ok(Self { percent }),
            _ => Err(MalformedPercentage { complete: COMPLETE }),
        }
    }
}
