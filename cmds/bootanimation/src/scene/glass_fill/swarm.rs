// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The swarm: the tiles that fly in from the stars to build the logo at the start and carry it
//! away at the exit, dust that drifts down the screen and swirls around the logo, and stardust
//! that drifts off the head of the progress line. Its sprites are made once, and the swarm shader
//! moves them by the moments of each frame.

mod random;

use std::{array, iter};

use num_traits::ToPrimitive;

use self::random::Mulberry32;
use super::logo::{Orientation, Piece, Triangle, LOGO_CENTER_Y, LOGO_SIZE};
use super::{Moments, ScreenPoint, BAR_HALF_WIDTH, BAR_Y, COMMON};
use crate::error::Error;
use crate::renderer::{Blending, Mesh, Pixels, Program, Renderer, ShaderConstant, Uniform, Window};

const SWARM_VERTEX: &str = include_str!("../../shaders/swarm_vertex.glsl");
const SWARM_FRAGMENT: &str = include_str!("../../shaders/swarm_fragment.glsl");

/// The height of the window in device pixels, the unit of the swarm shader: the pixels of a
/// 1080 x 2400 screen, the screen the places and sizes of the swarm are chosen for. The places of
/// the swarm are `Pixels` of the window scaled to this height.
const DEVICE_HEIGHT: f32 = 2400.0;

/// The seed of the random numbers of the swarm.
const SEED: u32 = 0x1b00;
/// How many dust sprites drift over each square device pixel of the window: 1400 over a window of
/// 1080 x 2400 device pixels. A product choice for how dense the dust looks, so a wider window gets
/// more dust and a narrower one less.
const DUST_DENSITY: f32 = 1400.0 / (1080.0 * 2400.0);
/// How many sprites of stardust drift off the head of the progress line at once. A product choice
/// for how the head of the line looks, the same on every window.
const SPARK_COUNT: usize = 14;
/// How far the homes of dust lie past the left and right edge of the window, in device pixels.
const DUST_OVERHANG_X: f32 = 40.0;
/// How far the homes of dust lie past the top and bottom edge of the window, in device pixels.
/// The swarm shader wraps dust around at the same distance above and below the window.
const DUST_OVERHANG_Y: f32 = 120.0;
/// The top and bottom of the band of the window that the stars of the tiles lie in, in device
/// pixels.
const STAR_BAND: [f32; 2] = [60.0, 2340.0];

// The flight of a tile, product choices for how the tiles build the logo.

/// How far the control point of the curve of a flight lies beside the middle of the straight way
/// from the star to the home, in lengths of that way.
const FLIGHT_BEND: f32 = 0.55;
/// When the first tiles at the tip of a triangle start to fly, in seconds since the start of the
/// animation.
const FLIGHT_START: f32 = 0.3;
/// How much later the tiles at the back of a triangle start to fly than those at its tip, in
/// seconds.
const FLIGHT_START_TIP_TO_BACK: f32 = 1.1;
/// The span of the random time added to the start of each flight, in seconds.
const FLIGHT_START_SPREAD: f32 = 0.25;
/// The shortest flight, in seconds.
const FLIGHT_LENGTH: f32 = 1.0;
/// The span of the random time added to the length of each flight, in seconds.
const FLIGHT_LENGTH_SPREAD: f32 = 0.3;

/// What a sprite of the swarm is.
#[derive(Clone, Copy, Debug)]
enum Kind {
    /// A tile of the blue triangle of the logo.
    BlueTile,
    /// A tile of the light blue-white triangle of the logo.
    WhiteTile,
    Dust,
    Spark,
}

impl Kind {
    const ALL: [Self; 4] = [Self::BlueTile, Self::WhiteTile, Self::Dust, Self::Spark];

    /// The kind of the tiles of piece.
    fn of_tiles(piece: Piece) -> Self {
        match piece {
            Piece::Blue => Self::BlueTile,
            Piece::White => Self::WhiteTile,
        }
    }

    /// The code of the kind in `aKind.x`.
    fn code(self) -> u8 {
        match self {
            Self::BlueTile => 0,
            Self::WhiteTile => 1,
            Self::Dust => 2,
            Self::Spark => 3,
        }
    }

    /// The constant of the swarm shader with the code of the kind.
    fn shader_constant(self) -> ShaderConstant {
        let name = match self {
            Self::BlueTile => "KIND_BLUE_TILE",
            Self::WhiteTile => "KIND_WHITE_TILE",
            Self::Dust => "KIND_DUST",
            Self::Spark => "KIND_SPARK",
        };
        ShaderConstant::Int(name, i32::from(self.code()))
    }

    /// The code of the kind as the float attribute of the swarm shader, which reads it back as an
    /// int.
    fn attribute(self) -> f32 {
        f32::from(self.code())
    }
}

/// The values of this module that the swarm shader uses, under the same names.
pub fn shader_constants() -> impl Iterator<Item = ShaderConstant> {
    [
        ShaderConstant::Float("DEVICE_HEIGHT", DEVICE_HEIGHT),
        ShaderConstant::Float("DUST_OVERHANG_Y", DUST_OVERHANG_Y),
    ]
    .into_iter()
    .chain(Kind::ALL.map(Kind::shader_constant))
}

/// A tile of a triangle of the logo, flying in from its star to its home, in device pixels.
#[derive(Clone, Copy, Debug)]
struct Tile {
    piece: Piece,
    orientation: Orientation,
    home: [f32; 2],
    star: [f32; 2],
    /// The control point of the curve of the flight.
    control: [f32; 2],
    /// When the flight starts, in seconds since the start of the animation.
    delay: f32,
    /// How long the flight takes, in seconds.
    duration: f32,
    /// The place of the tile in the gradient of the colors of its triangle, 0 to 1.
    gradient: f32,
    /// The place of the tile from the tip of its triangle to its back, 0 to 1.
    from_tip: f32,
}

/// A sprite of the swarm, before its random seeds.
#[derive(Clone, Copy, Debug)]
enum Sprite {
    Tile(Tile),
    /// Dust drifting from its home, in device pixels.
    Dust {
        home: [f32; 2],
    },
    /// Stardust, which the swarm shader places at the head of the progress line.
    Spark,
}

impl Sprite {
    /// The attributes of the sprite with seeds in the layout of the swarm shader: `aPlace`,
    /// `aFlight`, `aKind` and `aSeed`, one vec4 each.
    fn attributes(self, seeds: [f32; 4]) -> [[f32; 4]; 4] {
        match self {
            Self::Tile(tile) => [
                [tile.home[0], tile.home[1], tile.star[0], tile.star[1]],
                [tile.control[0], tile.control[1], tile.delay, tile.duration],
                [
                    Kind::of_tiles(tile.piece).attribute(),
                    tile.orientation.scale(),
                    tile.gradient,
                    tile.from_tip,
                ],
                seeds,
            ],
            Self::Dust { home } => Self::attributes_without_flight(Kind::Dust, home, seeds),
            Self::Spark => Self::attributes_without_flight(Kind::Spark, [0.0; 2], seeds),
        }
    }

    /// The attributes of dust or stardust of kind at home with seeds, upright. The swarm shader
    /// reads neither a star, a flight, a gradient nor an order from the tip for them, and the
    /// duration of 1 keeps a division by it defined.
    fn attributes_without_flight(kind: Kind, [x, y]: [f32; 2], seeds: [f32; 4]) -> [[f32; 4]; 4] {
        [
            [x, y, 0.0, 0.0],
            [0.0, 0.0, 0.0, 1.0],
            [kind.attribute(), Orientation::Upright.scale(), 0.0, 0.0],
            seeds,
        ]
    }
}

/// Where the stars of the tiles of piece lie across the window, from left to right, in device
/// pixels from the center of the logo.
fn star_span(piece: Piece) -> [f32; 2] {
    match piece {
        Piece::Blue => [-240.0, 600.0],
        Piece::White => [-600.0, 240.0],
    }
}

/// The tiles of triangle on the logo centered at logo, each flying in from a star at random.
/// Tiles and stars pair up in the order of their angle around the logo, so each tile comes from
/// about its own direction. Tiles near the tip start first.
fn tiles(triangle: &Triangle, random: &mut Mulberry32, logo: Pixels) -> Vec<Tile> {
    let centers = triangle.tile_centers();
    let stars: Vec<[f32; 2]> = centers
        .iter()
        .map(|_| {
            [logo.x + random.next_within(star_span(triangle.piece)), random.next_within(STAR_BAND)]
        })
        .collect();
    let tile_order = order_around_logo(centers.iter().map(|(center, _)| *center));
    let star_order = order_around_logo(stars.iter().map(|[x, y]| [x - logo.x, y - logo.y]));
    tile_order
        .into_iter()
        .zip(star_order)
        .map(|(tile, star)| {
            let (center, orientation) = centers[tile];
            let from_tip = triangle.fraction_from_tip(center);
            let home = [logo.x + center[0], logo.y + center[1]];
            let star = stars[star];
            let away = [home[0] - star[0], home[1] - star[1]];
            let control = [
                f32::midpoint(home[0], star[0]) - away[1] * FLIGHT_BEND,
                f32::midpoint(home[1], star[1]) + away[0] * FLIGHT_BEND,
            ];
            Tile {
                piece: triangle.piece,
                orientation,
                home,
                star,
                control,
                delay: FLIGHT_START
                    + from_tip * FLIGHT_START_TIP_TO_BACK
                    + random.next_fraction() * FLIGHT_START_SPREAD,
                duration: FLIGHT_LENGTH + random.next_fraction() * FLIGHT_LENGTH_SPREAD,
                gradient: triangle.gradient_at(center),
                from_tip,
            }
        })
        .collect()
}

/// The indices of points relative to the center of the logo in the order of their angle around
/// it, from -π to π. Points at the same angle keep their order.
fn order_around_logo(points: impl Iterator<Item = [f32; 2]>) -> Vec<usize> {
    let angles: Vec<f32> = points.map(|[x, y]| y.atan2(x)).collect();
    let mut order: Vec<usize> = (0..angles.len()).collect();
    order.sort_by(|&a, &b| angles[a].total_cmp(&angles[b]));
    order
}

/// How many dust sprites drift over device, the window in device pixels.
fn dust_count(device: Window) -> Result<usize, Error> {
    (DUST_DENSITY * device.width * device.height)
        .round()
        .to_usize()
        .ok_or(Error::DustCount { width: device.width, height: device.height })
}

/// The dust at random homes across device, the window in device pixels, the tiles of the
/// triangles of the logo centered at logo, then the stardust, each with four random seeds. The
/// random numbers go out in a fixed order, which decides where each sprite starts and flies: the
/// homes of the dust, the stars and flight times of the tiles of each triangle, then the seeds.
fn build_sprites(
    device: Window,
    logo: Pixels,
    triangles: &[Triangle],
) -> Result<Vec<[[f32; 4]; 4]>, Error> {
    let mut random = Mulberry32::new(SEED);
    let mut sprites: Vec<Sprite> = (0..dust_count(device)?)
        .map(|_| {
            let home = [
                -DUST_OVERHANG_X + random.next_fraction() * (device.width + 2.0 * DUST_OVERHANG_X),
                -DUST_OVERHANG_Y + random.next_fraction() * (device.height + 2.0 * DUST_OVERHANG_Y),
            ];
            Sprite::Dust { home }
        })
        .collect();
    for triangle in triangles {
        sprites.extend(tiles(triangle, &mut random, logo).into_iter().map(Sprite::Tile));
    }
    sprites.extend(iter::repeat_n(Sprite::Spark, SPARK_COUNT));
    Ok(sprites
        .into_iter()
        .map(|sprite| sprite.attributes(array::from_fn(|_| random.next_fraction())))
        .collect())
}

/// The swarm on the window.
pub struct Swarm<'a> {
    program: Program<'a>,
    sprites: Mesh<'a>,
    time: Uniform,
    since_exit: Uniform,
    progress: Uniform,
}

impl<'a> Swarm<'a> {
    /// Builds the swarm for window, the window of renderer, with constants as the GLSL part that
    /// follows `COMMON` in its vertex shader.
    pub fn new(renderer: &'a Renderer, constants: &str, window: Window) -> Result<Self, Error> {
        let device = window.scaled_to_height(DEVICE_HEIGHT);
        let logo = ScreenPoint { x: 0.0, y: LOGO_CENTER_Y }.to_pixels(device);
        let logo_size = LOGO_SIZE * device.height;
        let triangles = Triangle::of_logo(logo_size);
        // Dust and stardust take the shape of the tiles of the blue triangle.
        let tile_corners = triangles.each_ref().map(Triangle::tile_corners);
        let bar_left = ScreenPoint { x: -BAR_HALF_WIDTH, y: BAR_Y }.to_pixels(device);
        let bar_right = ScreenPoint { x: BAR_HALF_WIDTH, y: BAR_Y }.to_pixels(device);

        let program = renderer.link_program(
            "swarm",
            &[COMMON, constants, SWARM_VERTEX],
            &[COMMON, SWARM_FRAGMENT],
        )?;
        let pixel = program.uniform(c"uPx")?;
        let device_size = program.uniform(c"uDevice")?;
        let tile = program.uniform(c"uTri")?;
        let logo_place = program.uniform(c"uLogo")?;
        let bar = program.uniform(c"uBarEnds")?;
        program.set_uniforms(|set| {
            set.float(pixel, device.height / window.height);
            set.vec2(device_size, [device.width, device.height]);
            set.vec3(logo_place, [logo.x, logo.y, logo_size]);
            set.vec3(bar, [bar_left.x, bar_right.x, bar_left.y]);
            set.vec2_array(tile, tile_corners.as_flattened())
        })?;
        Ok(Self {
            time: program.uniform(c"uTime")?,
            since_exit: program.uniform(c"uSinceExit")?,
            progress: program.uniform(c"uProgress")?,
            sprites: renderer.quad_sprites(&build_sprites(device, logo, &triangles)?)?,
            program,
        })
    }

    /// Draws the swarm over the window at moments, with the bar at progress.
    pub fn draw(&self, moments: Moments, progress: f32) {
        self.program.set_uniforms(|set| {
            set.float(self.time, moments.now);
            set.float(self.since_exit, moments.since_exit);
            set.float(self.progress, progress);
        });
        self.program.draw(&self.sprites, Blending::PremultipliedOver);
    }
}
