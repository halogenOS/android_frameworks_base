// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The Glass fill scene: a glass ribbon behind the logo, a halo around it, the glass logo that
//! fills with the boot progress, the tiles that build it at the start and carry it away at the
//! exit, the progress line with stardust drifting off its head, dust swirling around the logo, and
//! the percentage and the boot stage at the progress line.

mod boot_text;
mod logo;
mod swarm;

use std::time::Duration;

use self::boot_text::BootText;
use self::swarm::Swarm;
use super::Frame;
use crate::error::Error;
use crate::renderer::{
    Blending, Mesh, Pixels, Program, RenderTarget, Renderer, ShaderConstant, Texture, Uniform,
    Window,
};

/// The first part of the shaders of the scene, with the brand colors and the functions used by
/// more than one program. The constants written by `shader_constants` follow it.
const COMMON: &str = include_str!("../shaders/common.glsl");
const GLASS_SPACE: &str = include_str!("../shaders/glass_space.glsl");
const GLASS_FRAGMENT: &str = include_str!("../shaders/glass_fragment.glsl");
const GLASS_MAIN: &str = include_str!("../shaders/glass_main.glsl");
const FULL_SCREEN_VERTEX: &str = include_str!("../shaders/full_screen_vertex.glsl");
const BACKGROUND_RENDER: &str = include_str!("../shaders/background_render.glsl");
const LOGO_VERTEX: &str = include_str!("../shaders/logo_vertex.glsl");
const LOGO_GLASS: &str = include_str!("../shaders/logo_glass.glsl");
const BEHIND_FRAGMENT: &str = include_str!("../shaders/behind_fragment.glsl");
const LOGO_FRAGMENT: &str = include_str!("../shaders/logo_fragment.glsl");

/// A place on the window, in screen heights from its center, y up, as the glass shaders measure.
#[derive(Clone, Copy, Debug)]
struct ScreenPoint {
    x: f32,
    y: f32,
}

impl ScreenPoint {
    /// The place in the pixels of window.
    fn to_pixels(self, window: Window) -> Pixels {
        Pixels { x: window.width / 2.0 + self.x * window.height, y: window.height * (0.5 - self.y) }
    }
}

// Places, in screen heights from the center of the window, y up.

/// The height of the progress line.
const BAR_Y: f32 = -0.29;
/// Half the width of the progress line.
const BAR_HALF_WIDTH: f32 = 0.118;

// Times.

/// From the ready report to the start of the exit, while the bar fills up.
const EXIT_DELAY: Duration = Duration::from_millis(350);
/// From the start of the exit until the swarm starts to leave.
const LEAVE_DELAY: Duration = Duration::from_millis(600);
/// From the start of the exit until the screen is black and the lock screen can show.
const EXIT_LENGTH: Duration = Duration::from_millis(2650);
/// How the progress line and the text fade in after the intro, from the start of the animation.
const INTRO_FADE: Fade = Fade::new(Duration::from_millis(2000), Duration::from_millis(800));
/// How the text fades out, from the start of the exit.
const TEXT_FADE_OUT: Fade = Fade::new(Duration::ZERO, Duration::from_millis(300));
/// How the progress line fades out, from the start of the exit, once the text is gone.
const LINE_FADE_OUT: Fade = Fade::new(TEXT_FADE_OUT.end(), Duration::from_millis(300));

/// A fade along an S curve over a span of time.
#[derive(Clone, Copy, Debug)]
struct Fade {
    start: Duration,
    length: Duration,
}

impl Fade {
    const fn new(start: Duration, length: Duration) -> Self {
        Self { start, length }
    }

    const fn end(self) -> Duration {
        self.start.saturating_add(self.length)
    }

    /// How far the fade has gone at seconds after the moment it is counted from: 0 up to its
    /// start, 1 from its end on.
    fn at(self, seconds: f32) -> f32 {
        smooth01((seconds - self.start.as_secs_f32()) / self.length.as_secs_f32())
    }
}

/// 0 up to 0, 1 from 1 on, and an S curve between, as `smooth01` in the glass shader.
fn smooth01(x: f32) -> f32 {
    let x = x.clamp(0.0, 1.0);
    x * x * (3.0 - 2.0 * x)
}

/// The moments that the shaders of the scene move by in one frame, in seconds.
#[derive(Clone, Copy, Debug)]
struct Moments {
    /// The time since the animation started.
    now: f32,
    /// The time since the exit started, 0 until it starts.
    since_exit: f32,
    /// The opacity of the progress line, from 0 to 1.
    line_opacity: f32,
    /// The opacity of the percentage and the stage line, from 0 to 1.
    text_opacity: f32,
}

impl Moments {
    fn of(frame: &Frame) -> Self {
        let now = frame.elapsed.as_secs_f32();
        let since_exit = frame
            .ready
            .map_or(Duration::ZERO, |ready| frame.elapsed.saturating_sub(ready + EXIT_DELAY))
            .as_secs_f32();
        let intro = INTRO_FADE.at(now);
        Self {
            now,
            since_exit,
            line_opacity: intro * (1.0 - LINE_FADE_OUT.at(since_exit)),
            text_opacity: intro * (1.0 - TEXT_FADE_OUT.at(since_exit)),
        }
    }
}

/// The Glass fill scene on the window of a renderer.
pub struct GlassFill<'a> {
    behind: GlassPass<'a>,
    /// The texture that the behind pass draws into and the logo pass reads.
    behind_texture: RenderTarget<'a>,
    background: GlassPass<'a>,
    logo: GlassPass<'a>,
    swarm: Swarm<'a>,
    /// The percentage and the stage line, or None when they could not be built.
    text: Option<BootText<'a>>,
}

impl<'a> GlassFill<'a> {
    /// Builds the scene for the window of renderer.
    pub fn new(renderer: &'a Renderer) -> Result<Self, Error> {
        let window = renderer.window();
        let constants = shader_constants();
        Ok(Self {
            behind: GlassPass::behind(renderer, &constants, window)?,
            behind_texture: renderer.window_render_target()?,
            background: GlassPass::background(renderer, &constants, window)?,
            logo: GlassPass::logo(renderer, &constants, window)?,
            swarm: Swarm::new(renderer, &constants, window)?,
            text: BootText::new(renderer, window)
                .inspect_err(|error| {
                    log::error!("drawing without the text: {}", error.with_causes());
                })
                .ok(),
        })
    }
}

impl GlassFill<'_> {
    /// Draws frame into the window. Fails on a text that the scene cannot show.
    pub fn draw(&mut self, frame: &Frame) -> Result<(), Error> {
        let moments = Moments::of(frame);
        let progress = frame.progress.fraction();
        // The behind pass draws first, so the window is drawn into without a switch of the
        // framebuffer in between.
        self.behind_texture.draw_into(|| self.behind.draw(moments, progress));
        self.background.draw(moments, progress);
        self.logo.draw_over(self.behind_texture.texture(), moments, progress);
        self.swarm.draw(moments, progress);
        self.text.as_mut().map_or(Ok(()), |text| text.draw(frame, moments))
    }

    /// How long the bar takes to fill up after the system reported ready, until the exit starts.
    pub fn fill_length() -> Duration {
        EXIT_DELAY
    }

    /// How long the scene keeps drawing after the system reported ready, to play its exit.
    pub fn exit_length() -> Duration {
        EXIT_DELAY + EXIT_LENGTH
    }
}

/// The values that the Rust code of the scene shares with its shaders, as the GLSL part that
/// follows `COMMON`.
fn shader_constants() -> String {
    logo::SHADER_CONSTANTS
        .into_iter()
        .chain([ShaderConstant::Float("LEAVE_DELAY", LEAVE_DELAY.as_secs_f32())])
        .chain(swarm::shader_constants())
        .map(|constant| constant.to_string())
        .collect()
}

/// The parts of the fragment shader of a glass pass, with constants from `shader_constants` and
/// then the parts of the pass, which end with its main function.
fn glass_fragment<'p>(constants: &'p str, pass: &[&'p str]) -> Vec<&'p str> {
    [COMMON, constants, GLASS_SPACE, GLASS_FRAGMENT]
        .into_iter()
        .chain(pass.iter().copied())
        .collect()
}

/// A pass of the glass shaders on the pixels covered by its mesh.
struct GlassPass<'a> {
    program: Program<'a>,
    mesh: Mesh<'a>,
    time: Uniform,
    since_exit: Uniform,
    progress: Uniform,
    /// The opacity of the progress line, in the passes that draw it.
    line_opacity: Option<Uniform>,
}

impl<'a> GlassPass<'a> {
    /// The pass over every pixel: the ribbon and the halo behind the logo, and the progress line.
    fn background(renderer: &'a Renderer, constants: &str, window: Window) -> Result<Self, Error> {
        let program = renderer.link_program(
            "background",
            &[FULL_SCREEN_VERTEX],
            &glass_fragment(constants, &[BACKGROUND_RENDER, GLASS_MAIN]),
        )?;
        let line_opacity = program.uniform(c"uLineOpacity")?;
        Self::new(program, renderer.full_screen_triangle(), window, Some(line_opacity))
    }

    /// The pass that draws the scene behind the front triangle of the logo, the ribbon, the halo
    /// and the blue triangle, on the rectangle that contains the glass, for the logo pass to read.
    /// The blur of the front triangle reads that scene ten times for each pixel, so drawing it
    /// once into a texture saves rendering it again for every sample.
    fn behind(renderer: &'a Renderer, constants: &str, window: Window) -> Result<Self, Error> {
        let program = renderer.link_program(
            "behind",
            &[COMMON, constants, GLASS_SPACE, LOGO_VERTEX],
            &glass_fragment(constants, &[LOGO_GLASS, BEHIND_FRAGMENT]),
        )?;
        Self::new(program, renderer.quad(), window, None)
    }

    /// The pass that draws the logo and the progress line over the background, on the rectangle
    /// that contains the glass, so the costly shading of the glass runs only on those pixels. It
    /// reads the scene behind the front triangle from the texture drawn by the behind pass.
    fn logo(renderer: &'a Renderer, constants: &str, window: Window) -> Result<Self, Error> {
        let program = renderer.link_program(
            "logo",
            &[COMMON, constants, GLASS_SPACE, LOGO_VERTEX],
            &glass_fragment(constants, &[LOGO_GLASS, LOGO_FRAGMENT, GLASS_MAIN]),
        )?;
        let line_opacity = program.uniform(c"uLineOpacity")?;
        Self::new(program, renderer.quad(), window, Some(line_opacity))
    }

    fn new(
        program: Program<'a>,
        mesh: Mesh<'a>,
        window: Window,
        line_opacity: Option<Uniform>,
    ) -> Result<Self, Error> {
        let resolution = program.uniform(c"uRes")?;
        let bar = program.uniform(c"uBar")?;
        program.set_uniforms(|set| {
            set.vec2(resolution, [window.width, window.height]);
            set.vec2(bar, [BAR_Y, BAR_HALF_WIDTH]);
        });
        Ok(Self {
            time: program.uniform(c"uTime")?,
            since_exit: program.uniform(c"uSinceExit")?,
            progress: program.uniform(c"uProgress")?,
            line_opacity,
            program,
            mesh,
        })
    }

    /// Draws the pass at moments with the bar at progress.
    fn draw(&self, moments: Moments, progress: f32) {
        self.set_uniforms(moments, progress);
        self.program.draw(&self.mesh, Blending::Replace);
    }

    /// Draws the pass at moments with the bar at progress, its sampler reading behind.
    fn draw_over(&self, behind: &Texture<'_>, moments: Moments, progress: f32) {
        self.set_uniforms(moments, progress);
        self.program.draw_textured(&self.mesh, behind, Blending::Replace);
    }

    fn set_uniforms(&self, moments: Moments, progress: f32) {
        self.program.set_uniforms(|set| {
            set.float(self.time, moments.now);
            set.float(self.since_exit, moments.since_exit);
            set.float(self.progress, progress);
            if let Some(line_opacity) = self.line_opacity {
                set.float(line_opacity, moments.line_opacity);
            }
        });
    }
}
