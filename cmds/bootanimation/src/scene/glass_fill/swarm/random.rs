// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The random numbers that place the swarm. They follow a fixed seed, so every boot draws the same
//! swarm.

/// The pseudorandom numbers of the mulberry32 generator.
pub struct Mulberry32(u32);

impl Mulberry32 {
    /// The numbers that follow seed.
    pub fn new(seed: u32) -> Self {
        Self(seed)
    }

    /// The next number from 0 to 1, rounded to f32, which can round it up to 1.
    pub fn next_fraction(&mut self) -> f32 {
        self.0 = self.0.wrapping_add(0x6D2B_79F5);
        let seed = self.0;
        let mut t = (seed ^ (seed >> 15)).wrapping_mul(1 | seed);
        t = t.wrapping_add((t ^ (t >> 7)).wrapping_mul(61 | t)) ^ t;
        // f32 has no lossless conversion from u32, so the halves convert on their own and add
        // up with one rounding.
        let [b0, b1, b2, b3] = (t ^ (t >> 14)).to_be_bytes();
        let high = f32::from(u16::from_be_bytes([b0, b1]));
        let low = f32::from(u16::from_be_bytes([b2, b3]));
        (high * 65_536.0 + low) / 4_294_967_296.0
    }

    /// The next number from the start to the end of span.
    pub fn next_within(&mut self, [start, end]: [f32; 2]) -> f32 {
        start + self.next_fraction() * (end - start)
    }
}
