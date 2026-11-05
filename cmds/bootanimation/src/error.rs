// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The operations of the animation that can fail, each with the error that caused it.

use std::ffi::CStr;
use std::num::{ParseIntError, TryFromIntError};
use std::{fmt, io, iter};

use gl::types::GLenum;
use khronos_egl as egl;
use rustutils::android::system_properties::PropertyWatcherError;
use skrifa::outline::DrawError;
use skrifa::raw::ReadError;

use crate::renderer::{RasterizeError, Stage};

/// An operation of the animation that failed.
#[derive(Debug)]
pub enum Error {
    /// `SurfaceFlinger` did not create the layer. status is the negative `status_t` of libgui.
    Layer {
        status: i32,
    },
    /// `boot_surface_create` returned 0 but no layer.
    MissingLayer,
    /// The layer has no window.
    MissingWindow,
    /// A side of the window of the layer, width x height pixels, is not from 0 to 65535 pixels.
    WindowSize {
        width: i32,
        height: i32,
        cause: TryFromIntError,
    },
    /// The thread that watches the property called name did not start.
    PropertyWatchThread {
        name: &'static str,
        cause: io::Error,
    },
    EglLoad(egl::LoadError<libloading::Error>),
    /// EGL returned no display for `EGL_DEFAULT_DISPLAY`.
    Display,
    Initialize(egl::Error),
    ChooseConfig(egl::Error),
    /// EGL has no config with 8 bit RGB for OpenGL ES 3 on a window.
    NoConfig,
    Context(egl::Error),
    WindowSurface(egl::Error),
    MakeCurrent(egl::Error),
    SwapInterval(egl::Error),
    Swap(egl::Error),
    /// OpenGL ES did not create a shader object for the program called program.
    CreateShader {
        program: &'static str,
        stage: Stage,
    },
    /// A shader of the program called program did not compile. log is the info log of OpenGL ES.
    CompileShader {
        program: &'static str,
        stage: Stage,
        log: String,
    },
    /// OpenGL ES did not create a program object for the program called program.
    CreateProgram {
        program: &'static str,
    },
    /// The program called program did not link. log is the info log of OpenGL ES.
    LinkProgram {
        program: &'static str,
        log: String,
    },
    /// The program called program has no active uniform called uniform.
    MissingUniform {
        program: &'static str,
        uniform: &'static CStr,
    },
    /// The shader of stage of the program called program has more parts, or a longer part, than
    /// OpenGL ES takes.
    ShaderTooLarge {
        program: &'static str,
        stage: Stage,
        cause: TryFromIntError,
    },
    /// A uniform array of length elements is longer than OpenGL ES takes.
    UniformArrayTooLong {
        length: usize,
        cause: TryFromIntError,
    },
    /// A mesh of vertices corners, drawn instances times, has more vertices, instances or bytes
    /// than OpenGL ES takes.
    MeshTooLarge {
        vertices: usize,
        instances: usize,
        cause: TryFromIntError,
    },
    PropertyRead {
        name: &'static str,
        cause: PropertyWatcherError,
    },
    PropertyWait {
        name: &'static str,
        cause: PropertyWatcherError,
    },
    PropertyNotNumber {
        name: &'static str,
        value: String,
        cause: ParseIntError,
    },
    /// The texture of the size width x height is larger than limit, the largest side that OpenGL
    /// ES takes on this device.
    TextureTooLarge {
        width: usize,
        height: usize,
        limit: i32,
    },
    /// The texels for a texture of width x height texels are texels, another number.
    TexelCount {
        width: usize,
        height: usize,
        texels: usize,
    },
    /// OpenGL ES cannot draw into a texture through a framebuffer. status is what
    /// `CheckFramebufferStatus` returned.
    IncompleteFramebuffer {
        status: GLenum,
    },
    FontRead {
        path: &'static str,
        cause: io::Error,
    },
    FontParse {
        path: &'static str,
        cause: ReadError,
    },
    /// The font has no advance for the glyph of character.
    GlyphAdvance {
        character: char,
    },
    GlyphOutline {
        character: char,
        cause: DrawError,
    },
    GlyphRasterize {
        character: char,
        cause: RasterizeError,
    },
    /// The glyphs of a label take a texture of width x height texels, more than 65535 on a side.
    AtlasTooLarge {
        width: usize,
        height: usize,
        cause: TryFromIntError,
    },
    /// A text to show contains character, which is not one of the texts that its label was made
    /// for.
    UnshownCharacter {
        character: char,
    },
    /// The progress at fraction of the boot has no whole percentage from 0 to 255.
    ProgressPercent {
        fraction: f32,
    },
    /// The count of the dust sprites for a window of width x height device pixels is not a
    /// number of sprites.
    DustCount {
        width: f32,
        height: f32,
    },
}

impl Error {
    /// The message of the error followed by the messages of its causes.
    pub fn with_causes(&self) -> String {
        iter::successors(Some(self as &dyn std::error::Error), |error| error.source())
            .map(ToString::to_string)
            .collect::<Vec<_>>()
            .join(": ")
    }
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Self::Layer { status } => {
                write!(f, "could not create the layer for the boot animation: status {status}")
            }
            Self::MissingLayer => write!(f, "the layer was created without a handle to it"),
            Self::MissingWindow => write!(f, "the layer of the boot animation has no window"),
            Self::WindowSize { width, height, .. } => {
                write!(f, "the window of {width} x {height} has a side outside 0 to 65535 pixels")
            }
            Self::PropertyWatchThread { name, .. } => {
                write!(f, "could not start the thread that watches {name}")
            }
            Self::EglLoad(_) => write!(f, "could not load libEGL"),
            Self::Display => write!(f, "could not get an EGL display"),
            Self::Initialize(_) => write!(f, "could not initialize the EGL display"),
            Self::ChooseConfig(_) => write!(f, "could not list the EGL configs"),
            Self::NoConfig => write!(f, "could not find an EGL config for ES 3"),
            Self::Context(_) => write!(f, "could not create the OpenGL ES context"),
            Self::WindowSurface(_) => write!(f, "could not create the EGL window surface"),
            Self::MakeCurrent(_) => write!(f, "could not make the OpenGL ES context current"),
            Self::SwapInterval(_) => write!(f, "could not set one swap per vsync"),
            Self::Swap(_) => write!(f, "could not show the frame"),
            Self::CreateShader { program, stage } => {
                write!(f, "could not create the {stage} shader of the {program} program")
            }
            Self::CompileShader { program, stage, log } => {
                write!(f, "could not compile the {stage} shader of the {program} program: {log}")
            }
            Self::CreateProgram { program } => write!(f, "could not create the {program} program"),
            Self::LinkProgram { program, log } => {
                write!(f, "could not link the {program} program: {log}")
            }
            Self::MissingUniform { program, uniform } => {
                write!(f, "the {program} program has no uniform {}", uniform.to_string_lossy())
            }
            Self::PropertyRead { name, .. } => write!(f, "could not read {name}"),
            Self::PropertyWait { name, .. } => write!(f, "could not wait for a change of {name}"),
            Self::PropertyNotNumber { name, value, .. } => {
                write!(f, "{name} is {value:?}, which is not a number")
            }
            Self::ShaderTooLarge { program, stage, .. } => {
                write!(f, "the {stage} shader of the {program} program is too large for OpenGL ES")
            }
            Self::UniformArrayTooLong { length, .. } => {
                write!(f, "a uniform array of {length} elements is too long for OpenGL ES")
            }
            Self::MeshTooLarge { vertices, instances, .. } => {
                write!(f, "a mesh of {vertices} vertices drawn {instances} times is too large")
            }
            Self::TextureTooLarge { width, height, limit } => {
                write!(f, "a texture of {width} x {height} is larger than the limit of {limit}")
            }
            Self::TexelCount { width, height, texels } => {
                write!(f, "a texture of {width} x {height} was given {texels} texels")
            }
            Self::IncompleteFramebuffer { status } => {
                write!(f, "could not draw into a texture: framebuffer status {status:#06x}")
            }
            Self::FontRead { path, .. } => write!(f, "could not read the font {path}"),
            Self::FontParse { path, .. } => write!(f, "{path} is not a font"),
            Self::GlyphAdvance { character } => {
                write!(f, "the font has no advance for the glyph of {character:?}")
            }
            Self::GlyphOutline { character, .. } => {
                write!(f, "could not read the outline of the glyph of {character:?}")
            }
            Self::GlyphRasterize { character, .. } => {
                write!(f, "could not rasterize the glyph of {character:?}")
            }
            Self::AtlasTooLarge { width, height, .. } => {
                write!(f, "the glyphs take {width} x {height} texels, more than 65535 on a side")
            }
            Self::UnshownCharacter { character } => {
                write!(f, "the label lacks a glyph for {character:?}")
            }
            Self::ProgressPercent { fraction } => {
                write!(f, "the progress {fraction} has no whole percentage from 0 to 255")
            }
            Self::DustCount { width, height } => {
                write!(f, "could not count the dust for a window of {width} x {height}")
            }
        }
    }
}

impl std::error::Error for Error {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Layer { .. }
            | Self::MissingLayer
            | Self::MissingWindow
            | Self::Display
            | Self::NoConfig
            | Self::CreateShader { .. }
            | Self::CompileShader { .. }
            | Self::CreateProgram { .. }
            | Self::LinkProgram { .. }
            | Self::MissingUniform { .. }
            | Self::TextureTooLarge { .. }
            | Self::TexelCount { .. }
            | Self::IncompleteFramebuffer { .. }
            | Self::GlyphAdvance { .. }
            | Self::UnshownCharacter { .. }
            | Self::ProgressPercent { .. }
            | Self::DustCount { .. } => None,
            Self::WindowSize { cause, .. }
            | Self::ShaderTooLarge { cause, .. }
            | Self::UniformArrayTooLong { cause, .. }
            | Self::MeshTooLarge { cause, .. }
            | Self::AtlasTooLarge { cause, .. } => Some(cause),
            Self::GlyphRasterize { cause, .. } => Some(cause),
            Self::EglLoad(cause) => Some(cause),
            Self::Initialize(cause)
            | Self::ChooseConfig(cause)
            | Self::Context(cause)
            | Self::WindowSurface(cause)
            | Self::MakeCurrent(cause)
            | Self::SwapInterval(cause)
            | Self::Swap(cause) => Some(cause),
            Self::PropertyRead { cause, .. } | Self::PropertyWait { cause, .. } => Some(cause),
            Self::PropertyNotNumber { cause, .. } => Some(cause),
            Self::PropertyWatchThread { cause, .. } | Self::FontRead { cause, .. } => Some(cause),
            Self::FontParse { cause, .. } => Some(cause),
            Self::GlyphOutline { cause, .. } => Some(cause),
        }
    }
}
