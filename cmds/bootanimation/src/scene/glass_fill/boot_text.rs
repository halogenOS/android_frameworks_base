// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The percentage above the right end of the progress line and the stage of the boot under its
//! middle, in Roboto. They fade in after the intro and out at the exit.

use std::iter;

use super::{Moments, ScreenPoint, BAR_HALF_WIDTH, BAR_Y};
use crate::boot_stage::{BootStage, Step};
use crate::error::Error;
use crate::renderer::{Align, Font, Label, Renderer, Style, TextProgram, Window};
use crate::scene::Frame;

/// The font of the text, as the system installs it.
const FONT_PATH: &str = "/system/fonts/Roboto-Regular.ttf";
/// The size of the text in screen heights.
const SIZE: f32 = 0.0168;
const PERCENT_WEIGHT: f32 = 500.0;
const STAGE_WEIGHT: f32 = 400.0;
/// How far the baseline of the percentage lies above the progress line, in screen heights.
const PERCENT_RAISE: f32 = 0.012;
/// How far the baseline of the stage line lies below the progress line, in screen heights.
const STAGE_DROP: f32 = 0.03;
/// The colors of the text, in red, green, blue and alpha from 0 to 1.
const PERCENT_COLOR: [f32; 4] = [226.0 / 255.0, 243.0 / 255.0, 1.0, 0.92];
const STAGE_COLOR: [f32; 4] = [196.0 / 255.0, 222.0 / 255.0, 245.0 / 255.0, 0.6];

/// The line under the progress line.
#[derive(Clone, Copy, Debug)]
enum StageLine {
    /// Before the first report of a stage.
    Starting,
    Stage(BootStage),
    /// The app update, with its reported progress.
    Updating(Step),
    /// From the ready report on.
    Ready,
}

impl StageLine {
    /// The line of frame.
    fn of(frame: &Frame) -> Self {
        match (frame.ready, frame.step, frame.stage) {
            (Some(_), _, _) => Self::Ready,
            (None, Some(step), _) => Self::Updating(step),
            (None, None, Some(stage)) => Self::Stage(stage),
            (None, None, None) => Self::Starting,
        }
    }

    /// Every line that can show.
    fn all() -> impl Iterator<Item = Self> {
        iter::once(Self::Starting)
            .chain(BootStage::all().map(Self::Stage))
            .chain(Step::all().map(Self::Updating))
            .chain(iter::once(Self::Ready))
    }

    fn text(self) -> String {
        match self {
            Self::Starting => "Starting system services".to_owned(),
            Self::Stage(stage) => stage_label(stage).to_owned(),
            Self::Updating(step) => {
                format!("{} ({})", stage_label(BootStage::AppUpdate), percent_text(step.percent()))
            }
            Self::Ready => "Ready".to_owned(),
        }
    }
}

/// The text under the progress line while the boot is at stage.
fn stage_label(stage: BootStage) -> &'static str {
    match stage {
        BootStage::SystemServer => "Starting system server",
        BootStage::Packages => "Scanning installed apps",
        BootStage::AppUpdate => "Updating installed apps",
        BootStage::Services => "Starting core services",
        BootStage::Interface => "Loading the system interface",
    }
}

/// The percentage of frame: 100 from the ready report on, and below 100 before it, even when the
/// bar has crept up to its end.
fn shown_percent(frame: &Frame) -> Result<u8, Error> {
    if frame.ready.is_some() {
        Ok(100)
    } else {
        Ok(frame.progress.percent()?.min(99))
    }
}

/// Every percentage that can show.
fn all_percents() -> impl Iterator<Item = u8> {
    0..=100
}

fn percent_text(percent: u8) -> String {
    format!("{percent}%")
}

/// The percentage and the stage line on the window.
pub struct BootText<'a> {
    program: TextProgram<'a>,
    percent: Label<'a>,
    stage_line: Label<'a>,
}

impl<'a> BootText<'a> {
    /// Builds the text for window, the window of renderer.
    pub fn new(renderer: &'a Renderer, window: Window) -> Result<Self, Error> {
        let font = Font::read(FONT_PATH)?;
        let size = SIZE * window.height;
        let percents: Vec<String> = all_percents().map(percent_text).collect();
        let lines: Vec<String> = StageLine::all().map(StageLine::text).collect();
        let percent = Label::new(
            renderer,
            &font,
            Style { weight: PERCENT_WEIGHT, size },
            percents.iter().map(String::as_str),
            ScreenPoint { x: BAR_HALF_WIDTH, y: BAR_Y + PERCENT_RAISE }.to_pixels(window),
            Align::End,
        )?;
        let stage_line = Label::new(
            renderer,
            &font,
            Style { weight: STAGE_WEIGHT, size },
            lines.iter().map(String::as_str),
            ScreenPoint { x: 0.0, y: BAR_Y - STAGE_DROP }.to_pixels(window),
            Align::Center,
        )?;
        Ok(Self { program: TextProgram::new(renderer)?, percent, stage_line })
    }

    /// Draws the text of frame at moments over the window. Fails on a text whose characters its
    /// label was not made for.
    pub fn draw(&mut self, frame: &Frame, moments: Moments) -> Result<(), Error> {
        if moments.text_opacity <= 0.0 {
            return Ok(());
        }
        self.percent.show(&percent_text(shown_percent(frame)?))?;
        self.stage_line.show(&StageLine::of(frame).text())?;
        let faded = |[red, green, blue, opacity]: [f32; 4]| {
            [red, green, blue, opacity * moments.text_opacity]
        };
        self.program.draw(&self.percent, faded(PERCENT_COLOR));
        self.program.draw(&self.stage_line, faded(STAGE_COLOR));
        Ok(())
    }
}
