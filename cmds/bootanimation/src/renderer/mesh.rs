// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The vertex data that shader programs of OpenGL ES 3.0 draw.

use std::ffi::c_void;
use std::num::TryFromIntError;
use std::{mem, ptr};

use gl::types::{GLenum, GLsizei, GLsizeiptr, GLuint};

use super::Renderer;
use crate::error::Error;

/// The corners of the triangle that covers the window, in clip space.
const FULL_SCREEN_TRIANGLE: [[f32; 2]; 3] = [[-1.0, -1.0], [3.0, -1.0], [-1.0, 3.0]];

/// The corners of a quad from -1 to 1, in the order of a triangle strip.
const QUAD_CORNERS: [[f32; 2]; 4] = [[-1.0, -1.0], [1.0, -1.0], [-1.0, 1.0], [1.0, 1.0]];

/// The size of a vec4 attribute.
const VEC4_BYTES: usize = mem::size_of::<[f32; 4]>();

/// Vertex data and how a program reads it. It borrows the `Renderer`, so it cannot outlive the
/// context, and dropping it deletes the data.
pub struct Mesh<'a> {
    vertex_array: GLuint,
    buffers: Vec<GLuint>,
    primitive: GLenum,
    vertices: GLsizei,
    instances: GLsizei,
    renderer: &'a Renderer,
}

impl Renderer {
    /// The triangle that covers the window, its corners in clip space at location 0.
    pub fn full_screen_triangle(&self) -> Result<Mesh<'_>, Error> {
        Mesh::new(self, gl::TRIANGLES, &FULL_SCREEN_TRIANGLE, 1, |_| Ok(()))
    }

    /// A quad drawn once, its corners from -1 to 1 at location 0, for a vertex shader that places
    /// it.
    pub fn quad(&self) -> Result<Mesh<'_>, Error> {
        Mesh::new(self, gl::TRIANGLE_STRIP, &QUAD_CORNERS, 1, |_| Ok(()))
    }

    /// A quad drawn once for each of instances, its corners from -1 to 1 at location 0. The N
    /// vec4 attributes of an instance go to the locations 1 to N. Fails when OpenGL ES takes no
    /// mesh this large.
    pub fn quad_sprites<const N: usize>(
        &self,
        instances: &[[[f32; 4]; N]],
    ) -> Result<Mesh<'_>, Error> {
        Mesh::new(self, gl::TRIANGLE_STRIP, &QUAD_CORNERS, instances.len(), |mesh| {
            mesh.add_buffer(instances.as_flattened().as_flattened())?;
            let stride = GLsizei::try_from(N * VEC4_BYTES)?;
            for (location, attribute) in (1..).zip(0..N) {
                let offset = ptr::without_provenance::<c_void>(attribute * VEC4_BYTES);
                // SAFETY: The context is current on this thread, asserted by Mesh::new.
                // VertexAttribPointer takes offset as a byte offset into the buffer bound by
                // add_buffer and reads nothing through it. Each instance takes stride bytes of that
                // buffer, and the attribute at offset ends within them.
                unsafe {
                    gl::EnableVertexAttribArray(location);
                    gl::VertexAttribPointer(location, 4, gl::FLOAT, gl::FALSE, stride, offset);
                    gl::VertexAttribDivisor(location, 1);
                }
            }
            Ok(())
        })
    }
}

impl<'a> Mesh<'a> {
    /// A mesh of the vec2 corners at location 0, drawn as primitive once for each of instances.
    /// `add_attributes` adds the data of the instances while the vertex array is bound, and fails
    /// on a size that OpenGL ES does not take. Fails when OpenGL ES takes no mesh this large.
    fn new(
        renderer: &'a Renderer,
        primitive: GLenum,
        corners: &[[f32; 2]],
        instances: usize,
        add_attributes: impl FnOnce(&mut Self) -> Result<(), TryFromIntError>,
    ) -> Result<Self, Error> {
        let too_large = |cause| Error::MeshTooLarge { vertices: corners.len(), instances, cause };
        let gl_vertices = GLsizei::try_from(corners.len()).map_err(too_large)?;
        let gl_instances = GLsizei::try_from(instances).map_err(too_large)?;
        renderer.assert_current();
        let mut vertex_array = 0;
        // SAFETY: The context is current on this thread, asserted above. GenVertexArrays writes one
        // name through the pointer, which points to the local vertex_array.
        unsafe {
            gl::GenVertexArrays(1, &raw mut vertex_array);
            gl::BindVertexArray(vertex_array);
        }
        let mut mesh = Self {
            vertex_array,
            buffers: Vec::new(),
            primitive,
            vertices: gl_vertices,
            instances: gl_instances,
            renderer,
        };
        mesh.add_buffer(corners.as_flattened()).map_err(too_large)?;
        // SAFETY: The context is current on this thread, asserted above. VertexAttribPointer takes
        // the null pointer as the byte offset 0 into the buffer bound by add_buffer and reads
        // nothing through it.
        unsafe {
            gl::EnableVertexAttribArray(0);
            gl::VertexAttribPointer(0, 2, gl::FLOAT, gl::FALSE, 0, ptr::null());
        }
        add_attributes(&mut mesh).map_err(too_large)?;
        // SAFETY: The context is current on this thread, asserted above, and BindVertexArray reads
        // only the plain value passed to it.
        unsafe { gl::BindVertexArray(0) };
        Ok(mesh)
    }

    /// Copies data into a new buffer and binds it, so the attributes set next read from it. Fails
    /// when OpenGL ES takes no buffer this large.
    fn add_buffer(&mut self, data: &[f32]) -> Result<(), TryFromIntError> {
        let size = GLsizeiptr::try_from(mem::size_of_val(data))?;
        let mut buffer = 0;
        // SAFETY: Mesh::new asserts that the context is current on this thread. GenBuffers writes
        // one name through the pointer, which points to the local buffer.
        unsafe { gl::GenBuffers(1, &raw mut buffer) };
        self.buffers.push(buffer);
        // SAFETY: Mesh::new asserts that the context is current on this thread. BufferData copies
        // size bytes from the pointer, the start of data, which holds size bytes.
        unsafe {
            gl::BindBuffer(gl::ARRAY_BUFFER, buffer);
            gl::BufferData(gl::ARRAY_BUFFER, size, data.as_ptr().cast(), gl::STATIC_DRAW);
        }
        Ok(())
    }

    /// Draws the mesh with the program in use. The context has to be current on this thread.
    pub(super) fn draw(&self) {
        // SAFETY: The only caller, Program::draw, asserts that the context is current on this
        // thread, and these calls read only the plain values passed to them.
        unsafe {
            gl::BindVertexArray(self.vertex_array);
            gl::DrawArraysInstanced(self.primitive, 0, self.vertices, self.instances);
            gl::BindVertexArray(0);
        }
    }
}

impl Drop for Mesh<'_> {
    fn drop(&mut self) {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above. DeleteVertexArrays reads
        // one name from the field vertex_array, and each DeleteBuffers one name from its buffer.
        unsafe {
            gl::DeleteVertexArrays(1, &raw const self.vertex_array);
            for buffer in &self.buffers {
                gl::DeleteBuffers(1, buffer);
            }
        }
    }
}
