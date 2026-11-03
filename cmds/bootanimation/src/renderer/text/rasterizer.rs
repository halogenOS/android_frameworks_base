// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The rasterizer of glyph outlines: for each pixel, the part of it that the outline covers by
//! the nonzero rule. Curves become lines, and each line adds the area it leaves to its right in
//! the cells of the rows it crosses; summing the cells of a row from the left gives the coverage.

use num_traits::ToPrimitive;
use skrifa::outline::pen::PathElement;

/// The largest distance in pixels between a curve and the lines that stand in for it, a tenth of a
/// pixel: less than the steps of antialiasing can show.
const TOLERANCE: f32 = 0.1;

/// The coverage of an outline on the pixels it touches.
pub struct Bitmap {
    pub width: u16,
    pub height: u16,
    /// width x height bytes, row by row from the top, from 0 for a pixel that is not covered to
    /// 255 for one covered whole.
    pub coverage: Vec<u8>,
    /// The top left corner of the bitmap from the origin of the outline, in pixels, y down.
    pub offset: [f32; 2],
}

/// An outline that does not fit into a bitmap of 65535 x 65535 pixels.
#[derive(Debug)]
pub struct TooLarge;

/// The coverage of path, an outline in pixels, y up. None when it covers no pixel.
pub fn rasterize(path: &[PathElement]) -> Result<Option<Bitmap>, TooLarge> {
    // The control points of each curve hold the curve between them.
    let points: Vec<[f32; 2]> = path
        .iter()
        .flat_map(|element| match *element {
            PathElement::MoveTo { x, y } | PathElement::LineTo { x, y } => vec![[x, y]],
            PathElement::QuadTo { cx0, cy0, x, y } => vec![[cx0, cy0], [x, y]],
            PathElement::CurveTo { cx0, cy0, cx1, cy1, x, y } => {
                vec![[cx0, cy0], [cx1, cy1], [x, y]]
            }
            PathElement::Close => Vec::new(),
        })
        .collect();
    let bound = |side: fn(f32, f32) -> f32, axis: usize| {
        points.iter().map(|point| point[axis]).reduce(side).unwrap_or_default()
    };
    let left = bound(f32::min, 0).floor();
    let top = bound(f32::max, 1).ceil();
    let pixels = |extent: f32| extent.to_u16().ok_or(TooLarge);
    let width = pixels(bound(f32::max, 0).ceil() - left)?;
    let height = pixels(top - bound(f32::min, 1).floor())?;
    if width == 0 || height == 0 {
        return Ok(None);
    }

    let to_bitmap = |x: f32, y: f32| Point { x: x - left, y: top - y };
    let mut cells = Cells::new(width, height);
    let mut start = Point { x: 0.0, y: 0.0 };
    let mut current = start;
    for element in path {
        match *element {
            PathElement::MoveTo { x, y } => {
                cells.add_line(current, start);
                start = to_bitmap(x, y);
                current = start;
            }
            PathElement::LineTo { x, y } => {
                let end = to_bitmap(x, y);
                cells.add_line(current, end);
                current = end;
            }
            PathElement::QuadTo { cx0, cy0, x, y } => {
                let end = to_bitmap(x, y);
                cells.add_quadratic(current, to_bitmap(cx0, cy0), end);
                current = end;
            }
            PathElement::CurveTo { cx0, cy0, cx1, cy1, x, y } => {
                let end = to_bitmap(x, y);
                cells.add_cubic(current, to_bitmap(cx0, cy0), to_bitmap(cx1, cy1), end);
                current = end;
            }
            PathElement::Close => {
                cells.add_line(current, start);
                current = start;
            }
        }
    }
    cells.add_line(current, start);
    Ok(Some(Bitmap { width, height, coverage: cells.coverage(), offset: [left, -top] }))
}

/// A point in the pixels of a bitmap, from its top left corner, y down.
#[derive(Clone, Copy, Debug)]
struct Point {
    x: f32,
    y: f32,
}

impl Point {
    /// The point the part t of the way from self to other.
    fn towards(self, other: Self, t: f32) -> Self {
        Self { x: self.x + (other.x - self.x) * t, y: self.y + (other.y - self.y) * t }
    }
}

/// How far the middle point bends away from the line between its neighbors: the length of
/// `before - 2 middle + after`, the second difference of the three.
fn bend(before: Point, middle: Point, after: Point) -> f32 {
    (before.x - 2.0 * middle.x + after.x).hypot(before.y - 2.0 * middle.y + after.y)
}

/// The number of lines of equal parameter steps that stay within `TOLERANCE` of a curve whose
/// lines, with n of them, stray at most `stray / n²` from it.
fn line_count(stray: f32) -> u16 {
    (stray / TOLERANCE)
        .sqrt()
        .ceil()
        .max(1.0)
        .to_u16()
        .expect("a curve inside a bitmap of u16 sides")
}

/// The area that the lines of the outline leave to their right, in the cells of each row. A row
/// has two cells more than the bitmap has pixels, for lines on its right edge.
struct Cells {
    width: usize,
    cells: Vec<f32>,
}

impl Cells {
    fn new(width: u16, height: u16) -> Self {
        let width = usize::from(width);
        Self { width, cells: vec![0.0; (width + 2) * usize::from(height)] }
    }

    fn add_cubic(&mut self, start: Point, first: Point, second: Point, end: Point) {
        // The second derivative of the curve is at most 6 times the larger bend, and a line over a
        // parameter step of 1/n strays at most an eighth of that over n².
        let stray = 0.75 * bend(start, first, second).max(bend(first, second, end));
        let lines = line_count(stray);
        let mut previous = start;
        for step in 1..=lines {
            let t = f32::from(step) / f32::from(lines);
            let [a, b, c] =
                [start.towards(first, t), first.towards(second, t), second.towards(end, t)];
            let point = a.towards(b, t).towards(b.towards(c, t), t);
            self.add_line(previous, point);
            previous = point;
        }
    }

    fn add_quadratic(&mut self, start: Point, control: Point, end: Point) {
        // The second derivative of the curve is twice the bend, and a line over a parameter step
        // of 1/n strays at most an eighth of that over n².
        let lines = line_count(0.25 * bend(start, control, end));
        let mut previous = start;
        for step in 1..=lines {
            let t = f32::from(step) / f32::from(lines);
            let point = start.towards(control, t).towards(control.towards(end, t), t);
            self.add_line(previous, point);
            previous = point;
        }
    }

    /// Adds the line from start to end, split at the rows it crosses. A line going down adds its
    /// area, a line going up subtracts it.
    fn add_line(&mut self, start: Point, end: Point) {
        let (upper, lower, direction) = match start.y.total_cmp(&end.y) {
            std::cmp::Ordering::Less => (start, end, 1.0),
            std::cmp::Ordering::Greater => (end, start, -1.0),
            std::cmp::Ordering::Equal => return,
        };
        let x_per_y = (lower.x - upper.x) / (lower.y - upper.y);
        let x_at = |y: f32| upper.x + (y - upper.y) * x_per_y;
        let row = |y: f32| y.to_u16().expect("a point inside the bitmap");
        for row in row(upper.y.floor())..row(lower.y.ceil()) {
            let top = upper.y.max(f32::from(row));
            let bottom = lower.y.min(f32::from(row) + 1.0);
            self.add_in_row(
                usize::from(row),
                [x_at(top), x_at(bottom)],
                (bottom - top) * direction,
            );
        }
    }

    /// Adds the part of a line inside row that runs between the x of ends and is height high,
    /// split at the pixels it crosses. Each part adds the area right of it within its pixel to
    /// the cell of the pixel, and the rest of its height to the next cell.
    fn add_in_row(&mut self, row: usize, ends: [f32; 2], height: f32) {
        let stride = self.width + 2;
        let cells = &mut self.cells[row * stride..(row + 1) * stride];
        let [left, right] = [ends[0].min(ends[1]), ends[0].max(ends[1])];
        let mut column = left.floor();
        loop {
            let part_left = left.max(column);
            let part_right = right.min(column + 1.0);
            let part_height = if right > left {
                height * (part_right - part_left) / (right - left)
            } else {
                height
            };
            let middle = 0.5 * (part_left + part_right) - column;
            let cell = column.to_usize().expect("a point inside the bitmap");
            cells[cell] += part_height * (1.0 - middle);
            cells[cell + 1] += part_height * middle;
            column += 1.0;
            if column >= right {
                break;
            }
        }
    }

    /// The coverage of each pixel, row by row from the top: the sum of the cells up to it, where
    /// outlines on top of each other count once.
    fn coverage(&self) -> Vec<u8> {
        let to_byte = |area: f32| {
            (area.abs().min(1.0) * 255.0).round().to_u8().expect("a coverage from 0 to 255")
        };
        self.cells
            .chunks_exact(self.width + 2)
            .flat_map(|row| {
                row[..self.width].iter().scan(0.0, move |area, cell| {
                    *area += cell;
                    Some(to_byte(*area))
                })
            })
            .collect()
    }
}
