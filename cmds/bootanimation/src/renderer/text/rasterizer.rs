// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The rasterizer of glyph outlines: for each pixel, the part of it that the outline covers by
//! the nonzero rule. Curves become lines, and each line adds the area it leaves to its right in
//! the cells of the rows it crosses; summing the cells of a row from the left gives the coverage.

use num_traits::ToPrimitive;
use skrifa::outline::pen::PathElement;

use crate::error::Error;

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

/// A point of an outline, in the pixels of the bitmap made from it, that lies outside that bitmap.
struct OutsideBitmap(Point);

/// The coverage of path, the outline of the glyph of character in pixels, y up. None when it
/// covers no pixel. Fails on a point of the outline that is NaN or infinite, and on an outline more
/// than `u16::MAX` pixels wide or high.
pub fn rasterize(character: char, path: &[PathElement]) -> Result<Option<Bitmap>, Error> {
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
    if let Some(&[x, y]) =
        points.iter().find(|point| !point.iter().all(|coordinate| coordinate.is_finite()))
    {
        return Err(Error::GlyphNotFinite { character, x, y });
    }
    let bound = |side: fn(f32, f32) -> f32, axis: fn(&[f32; 2]) -> f32| {
        points.iter().map(axis).reduce(side).unwrap_or_default()
    };
    let [x_of, y_of]: [fn(&[f32; 2]) -> f32; 2] = [|&[x, _]| x, |&[_, y]| y];
    let left = bound(f32::min, x_of).floor();
    let top = bound(f32::max, y_of).ceil();
    let pixels = |extent: f32| extent.to_u16().ok_or(Error::GlyphTooLarge { character });
    let width = pixels(bound(f32::max, x_of).ceil() - left)?;
    let height = pixels(top - bound(f32::min, y_of).floor())?;
    if width == 0 || height == 0 {
        return Ok(None);
    }

    let mut cells = Cells::new(width, height);
    cells
        .add_path(path, |x, y| Point { x: x - left, y: top - y })
        .map_err(|OutsideBitmap(Point { x, y })| Error::GlyphOutsideBitmap { character, x, y })?;
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
/// lines, with n of them, stray at most `stray / n²` from it. A curve whose control points lie
/// inside a bitmap of u16 sides needs at most about 1700 lines, so the count always converts to a
/// u16.
fn line_count(stray: f32) -> u16 {
    (stray / TOLERANCE).sqrt().ceil().max(1.0).to_u16().unwrap_or(u16::MAX)
}

/// The area that the lines of the outline leave to their right, in the cells of each row. A row
/// has two cells more than the bitmap has pixels, for lines on its right edge. Rounding can put a
/// point of a line a little outside the bitmap: the end of a line that stands in for a curve, or
/// where a line crosses a row. `add_line` moves its y, and `add_in_row` its x, onto the edge.
struct Cells {
    width: usize,
    /// The bottom right corner of the bitmap.
    corner: Point,
    areas: Vec<f32>,
}

impl Cells {
    fn new(width: u16, height: u16) -> Self {
        let corner = Point { x: f32::from(width), y: f32::from(height) };
        let width = usize::from(width);
        Self { width, corner, areas: vec![0.0; (width + 2) * usize::from(height)] }
    }

    /// Adds the lines and curves of path, an outline whose points `to_bitmap` converts to the
    /// pixels of the bitmap.
    fn add_path(
        &mut self,
        path: &[PathElement],
        to_bitmap: impl Fn(f32, f32) -> Point,
    ) -> Result<(), OutsideBitmap> {
        let mut start = Point { x: 0.0, y: 0.0 };
        let mut current = start;
        for element in path {
            match *element {
                PathElement::MoveTo { x, y } => {
                    self.add_line(current, start)?;
                    start = to_bitmap(x, y);
                    current = start;
                }
                PathElement::LineTo { x, y } => {
                    let end = to_bitmap(x, y);
                    self.add_line(current, end)?;
                    current = end;
                }
                PathElement::QuadTo { cx0, cy0, x, y } => {
                    let end = to_bitmap(x, y);
                    self.add_quadratic(current, to_bitmap(cx0, cy0), end)?;
                    current = end;
                }
                PathElement::CurveTo { cx0, cy0, cx1, cy1, x, y } => {
                    let end = to_bitmap(x, y);
                    self.add_cubic(current, to_bitmap(cx0, cy0), to_bitmap(cx1, cy1), end)?;
                    current = end;
                }
                PathElement::Close => {
                    self.add_line(current, start)?;
                    current = start;
                }
            }
        }
        self.add_line(current, start)
    }

    fn add_cubic(
        &mut self,
        start: Point,
        first: Point,
        second: Point,
        end: Point,
    ) -> Result<(), OutsideBitmap> {
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
            self.add_line(previous, point)?;
            previous = point;
        }
        Ok(())
    }

    fn add_quadratic(
        &mut self,
        start: Point,
        control: Point,
        end: Point,
    ) -> Result<(), OutsideBitmap> {
        // The second derivative of the curve is twice the bend, and a line over a parameter step
        // of 1/n strays at most an eighth of that over n².
        let lines = line_count(0.25 * bend(start, control, end));
        let mut previous = start;
        for step in 1..=lines {
            let t = f32::from(step) / f32::from(lines);
            let point = start.towards(control, t).towards(control.towards(end, t), t);
            self.add_line(previous, point)?;
            previous = point;
        }
        Ok(())
    }

    /// Adds the line from start to end, split at the rows it crosses. A line going down adds its
    /// area, a line going up subtracts it.
    fn add_line(&mut self, start: Point, end: Point) -> Result<(), OutsideBitmap> {
        let [start, end] =
            [start, end].map(|point| Point { y: point.y.clamp(0.0, self.corner.y), ..point });
        let (upper, lower, direction) = match start.y.total_cmp(&end.y) {
            std::cmp::Ordering::Less => (start, end, 1.0),
            std::cmp::Ordering::Greater => (end, start, -1.0),
            std::cmp::Ordering::Equal => return Ok(()),
        };
        let x_per_y = (lower.x - upper.x) / (lower.y - upper.y);
        let x_at = |y: f32| upper.x + (y - upper.y) * x_per_y;
        let row_of = |point: Point, y: f32| y.to_u16().ok_or(OutsideBitmap(point));
        for row in row_of(upper, upper.y.floor())?..row_of(lower, lower.y.ceil())? {
            let top = upper.y.max(f32::from(row));
            let bottom = lower.y.min(f32::from(row) + 1.0);
            self.add_in_row(row, [x_at(top), x_at(bottom)], (bottom - top) * direction)?;
        }
        Ok(())
    }

    /// Adds the part of a line inside row that runs between the x of ends and is height high,
    /// split at the pixels it crosses. Each part adds the area right of it within its pixel to
    /// the cell of the pixel, and the rest of its height to the next cell.
    fn add_in_row(&mut self, row: u16, ends: [f32; 2], height: f32) -> Result<(), OutsideBitmap> {
        let outside_bitmap_error = |x: f32| OutsideBitmap(Point { x, y: f32::from(row) });
        let [left, right] =
            [ends[0].min(ends[1]), ends[0].max(ends[1])].map(|x| x.clamp(0.0, self.corner.x));
        let stride = self.width + 2;
        let first_cell = usize::from(row) * stride;
        let cells = self
            .areas
            .get_mut(first_cell..first_cell + stride)
            .ok_or(outside_bitmap_error(left))?;
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
            let [cell, next_cell] = column
                .to_usize()
                .and_then(|cell| cells.get_mut(cell..))
                .and_then(|rest| rest.first_chunk_mut::<2>())
                .ok_or(outside_bitmap_error(column))?;
            *cell += part_height * (1.0 - middle);
            *next_cell += part_height * middle;
            column += 1.0;
            if column >= right {
                return Ok(());
            }
        }
    }

    /// The coverage of each pixel, row by row from the top: the sum of the cells up to it, where
    /// outlines on top of each other count once.
    fn coverage(&self) -> Vec<u8> {
        // f32::min returns 1.0 for NaN, so the byte is always from 0 to 255.
        let to_byte = |area: f32| (area.abs().min(1.0) * 255.0).round().to_u8().unwrap_or(u8::MAX);
        self.areas
            .chunks_exact(self.width + 2)
            .flat_map(|row| {
                row.iter().take(self.width).scan(0.0, move |area, cell| {
                    *area += cell;
                    Some(to_byte(*area))
                })
            })
            .collect()
    }
}
