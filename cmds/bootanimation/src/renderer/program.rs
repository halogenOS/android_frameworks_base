// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Shader programs of OpenGL ES 3.0 and their uniforms.

use std::ffi::CStr;
use std::{fmt, iter};

use gl::types::{GLchar, GLenum, GLint, GLsizei, GLuint};

use super::mesh::Mesh;
use super::texture::Texture;
use super::Renderer;
use crate::error::Error;

/// The line that starts every shader: the shading language of OpenGL ES 3.0, the version of the
/// context that the renderer creates.
const VERSION_LINE: &str = "#version 300 es\n";

/// A stage of a shader program.
#[derive(Clone, Copy, Debug)]
pub enum Stage {
    Vertex,
    Fragment,
}

impl Stage {
    fn shader_type(self) -> GLenum {
        match self {
            Self::Vertex => gl::VERTEX_SHADER,
            Self::Fragment => gl::FRAGMENT_SHADER,
        }
    }
}

impl fmt::Display for Stage {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        f.write_str(match self {
            Self::Vertex => "vertex",
            Self::Fragment => "fragment",
        })
    }
}

/// How a draw combines its colors with the colors already in the window.
#[derive(Clone, Copy, Debug)]
pub enum Blending {
    /// The colors of the draw replace those in the window.
    Replace,
    /// The colors of the draw, premultiplied by their alpha, go over those in the window.
    PremultipliedOver,
}

impl Blending {
    /// Sets the blending of the following draws. The context has to be current on this thread.
    fn apply(self) {
        // SAFETY: The only caller, Program::draw, asserts that the context is current on this
        // thread, and these calls read only the plain values passed to them.
        unsafe {
            match self {
                Self::Replace => gl::Disable(gl::BLEND),
                Self::PremultipliedOver => {
                    gl::Enable(gl::BLEND);
                    gl::BlendFunc(gl::ONE, gl::ONE_MINUS_SRC_ALPHA);
                }
            }
        }
    }
}

/// A constant that Rust code shares with the shaders it links. Its `Display` is the GLSL
/// declaration of the constant, a part to link before the parts that use it.
#[derive(Clone, Copy, Debug)]
pub enum ShaderConstant {
    /// A `const float` of the name and value. The value has to be finite.
    Float(&'static str, f32),
    /// A `const int` of the name and value.
    Int(&'static str, i32),
}

impl fmt::Display for ShaderConstant {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match *self {
            // Debug writes the shortest decimal that reads back as the same f32, always with a
            // point or an exponent, so GLSL reads it as that float.
            Self::Float(name, value) => writeln!(f, "const float {name} = {value:?};"),
            Self::Int(name, value) => writeln!(f, "const int {name} = {value};"),
        }
    }
}

/// One integer parameter of a shader or a program, read with get: `GetShaderiv` or
/// `GetProgramiv`.
fn parameter(object: GLuint, name: GLenum, get: unsafe fn(GLuint, GLenum, *mut GLint)) -> GLint {
    let mut value = 0;
    // SAFETY: get is GetShaderiv or GetProgramiv. For the parameters read here, they write one
    // GLint through the pointer, which points to the local value.
    unsafe { get(object, name, &raw mut value) };
    value
}

/// The info log of a shader or a program, read with get and `read_log`: `GetShaderiv` and
/// `GetShaderInfoLog`, or `GetProgramiv` and `GetProgramInfoLog`.
fn info_log(
    object: GLuint,
    get: unsafe fn(GLuint, GLenum, *mut GLint),
    read_log: unsafe fn(GLuint, GLsizei, *mut GLsizei, *mut GLchar),
) -> String {
    // OpenGL ES reports the length as a GLint. One below zero means there is no log.
    let capacity = parameter(object, gl::INFO_LOG_LENGTH, get);
    let mut log = vec![0_u8; usize::try_from(capacity).unwrap_or_default()];
    let mut written = 0;
    // SAFETY: read_log is GetShaderInfoLog or GetProgramInfoLog. They write at most capacity bytes
    // into log, which holds capacity bytes, and the number of bytes written before the terminating
    // NUL into the local written.
    unsafe { read_log(object, capacity, &raw mut written, log.as_mut_ptr().cast()) };
    log.truncate(usize::try_from(written).unwrap_or_default());
    String::from_utf8_lossy(&log).trim_end().to_owned()
}

/// A compiled shader. Deleting it leaves the programs linked from it working.
struct Shader(GLuint);

impl Shader {
    /// Compiles the shader of stage for the program called program from parts. OpenGL ES joins
    /// the parts in order after the version line.
    fn compile(program: &'static str, stage: Stage, parts: &[&str]) -> Result<Self, Error> {
        // SAFETY: The caller asserts that the context is current on this thread, and CreateShader
        // reads only the plain value passed to it.
        let id = unsafe { gl::CreateShader(stage.shader_type()) };
        if id == 0 {
            return Err(Error::CreateShader { program, stage });
        }
        let shader = Self(id);
        let too_large = |cause| Error::ShaderTooLarge { program, stage, cause };
        let parts: Vec<&str> = iter::once(VERSION_LINE).chain(parts.iter().copied()).collect();
        let starts: Vec<*const GLchar> = parts.iter().map(|part| part.as_ptr().cast()).collect();
        let lengths = parts
            .iter()
            .map(|part| GLint::try_from(part.len()))
            .collect::<Result<Vec<_>, _>>()
            .map_err(too_large)?;
        let count = GLsizei::try_from(parts.len()).map_err(too_large)?;
        // SAFETY: ShaderSource reads as many pointers from starts and lengths from lengths as the
        // count passed, which is the length of both, and from each pointer as many bytes as its
        // length. Each pointer points to a string of that length, which outlives the call. A string
        // with a length needs no terminating NUL.
        unsafe {
            gl::ShaderSource(id, count, starts.as_ptr(), lengths.as_ptr());
            gl::CompileShader(id);
        }
        if parameter(id, gl::COMPILE_STATUS, gl::GetShaderiv) == GLint::from(gl::TRUE) {
            Ok(shader)
        } else {
            let log = info_log(id, gl::GetShaderiv, gl::GetShaderInfoLog);
            Err(Error::CompileShader { program, stage, log })
        }
    }
}

impl Drop for Shader {
    fn drop(&mut self) {
        // SAFETY: A Shader lives only inside Renderer::link_program, which asserts that the context
        // is current on this thread, and DeleteShader reads only the plain value passed to it.
        unsafe { gl::DeleteShader(self.0) };
    }
}

/// A linked shader program. It borrows the `Renderer`, so it cannot outlive the context, and
/// dropping it deletes the program.
pub struct Program<'a> {
    id: GLuint,
    name: &'static str,
    renderer: &'a Renderer,
}

/// The location of a uniform of a program.
#[derive(Clone, Copy, Debug)]
pub struct Uniform {
    program: GLuint,
    location: GLint,
}

/// Sets the uniforms of the program in use. It exists only during `Program::set_uniforms`.
pub struct Uniforms {
    program: GLuint,
}

impl Renderer {
    /// Links the program called name from the parts of its vertex and fragment shaders. OpenGL
    /// ES joins the parts of each shader in order after the version line of the shading
    /// language.
    pub fn link_program(
        &self,
        name: &'static str,
        vertex: &[&str],
        fragment: &[&str],
    ) -> Result<Program<'_>, Error> {
        self.assert_current();
        let vertex = Shader::compile(name, Stage::Vertex, vertex)?;
        let fragment = Shader::compile(name, Stage::Fragment, fragment)?;
        // SAFETY: The context is current on this thread, asserted above, and CreateProgram reads
        // nothing.
        let id = unsafe { gl::CreateProgram() };
        if id == 0 {
            return Err(Error::CreateProgram { program: name });
        }
        let program = Program { id, name, renderer: self };
        // SAFETY: The context is current on this thread, asserted above, and these calls read only
        // the plain values passed to them.
        unsafe {
            gl::AttachShader(id, vertex.0);
            gl::AttachShader(id, fragment.0);
            gl::LinkProgram(id);
        }
        if parameter(id, gl::LINK_STATUS, gl::GetProgramiv) == GLint::from(gl::TRUE) {
            Ok(program)
        } else {
            let log = info_log(id, gl::GetProgramiv, gl::GetProgramInfoLog);
            Err(Error::LinkProgram { program: name, log })
        }
    }
}

impl Program<'_> {
    /// The uniform called name. Fails when the program has no active uniform of that name, which
    /// is also the case for a uniform that its shaders declare but do not use.
    pub fn uniform(&self, name: &'static CStr) -> Result<Uniform, Error> {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above. GetUniformLocation reads
        // the string at the pointer up to its terminating NUL, and name is such a string that lives
        // for the whole program.
        let location = unsafe { gl::GetUniformLocation(self.id, name.as_ptr()) };
        if location < 0 {
            Err(Error::MissingUniform { program: self.name, uniform: name })
        } else {
            Ok(Uniform { program: self.id, location })
        }
    }

    /// Calls set with the setter of the uniforms of this program and returns what set returns.
    /// The uniforms keep their values until they are set again.
    pub fn set_uniforms<R>(&self, set: impl FnOnce(&Uniforms) -> R) -> R {
        self.use_program();
        set(&Uniforms { program: self.id })
    }

    /// Draws mesh with this program.
    pub fn draw(&self, mesh: &Mesh<'_>, blending: Blending) {
        self.use_program();
        blending.apply();
        mesh.draw();
    }

    /// Draws mesh with this program, whose sampler uniform reads texture.
    pub fn draw_textured(&self, mesh: &Mesh<'_>, texture: &Texture<'_>, blending: Blending) {
        texture.bind();
        self.draw(mesh, blending);
    }

    fn use_program(&self) {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above, and UseProgram reads only
        // the plain value passed to it.
        unsafe { gl::UseProgram(self.id) };
    }
}

impl Drop for Program<'_> {
    fn drop(&mut self) {
        self.renderer.assert_current();
        // SAFETY: The context is current on this thread, asserted above, and DeleteProgram reads
        // only the plain value passed to it.
        unsafe { gl::DeleteProgram(self.id) };
    }
}

impl Uniforms {
    /// Sets the float uniform to value.
    pub fn float(&self, uniform: Uniform, value: f32) {
        let location = self.location_of(uniform);
        // SAFETY: Uniforms exist only inside Program::set_uniforms, which asserts that the context
        // is current on this thread, and Uniform1f reads only the plain values passed to it.
        unsafe { gl::Uniform1f(location, value) };
    }

    /// Sets the vec2 uniform to value.
    pub fn vec2(&self, uniform: Uniform, [x, y]: [f32; 2]) {
        let location = self.location_of(uniform);
        // SAFETY: As in float, with Uniform2f.
        unsafe { gl::Uniform2f(location, x, y) };
    }

    /// Sets the vec3 uniform to value.
    pub fn vec3(&self, uniform: Uniform, [x, y, z]: [f32; 3]) {
        let location = self.location_of(uniform);
        // SAFETY: As in float, with Uniform3f.
        unsafe { gl::Uniform3f(location, x, y, z) };
    }

    /// Sets the vec4 uniform to value.
    pub fn vec4(&self, uniform: Uniform, [x, y, z, w]: [f32; 4]) {
        let location = self.location_of(uniform);
        // SAFETY: As in float, with Uniform4f.
        unsafe { gl::Uniform4f(location, x, y, z, w) };
    }

    /// Sets the vec2 array uniform to values, from its first element on. Fails when OpenGL ES
    /// takes no array this long.
    pub fn vec2_array(&self, uniform: Uniform, values: &[[f32; 2]]) -> Result<(), Error> {
        let location = self.location_of(uniform);
        let count = GLsizei::try_from(values.len())
            .map_err(|cause| Error::UniformArrayTooLong { length: values.len(), cause })?;
        // SAFETY: Uniforms exist only inside Program::set_uniforms, which asserts that the context
        // is current on this thread. Uniform2fv reads two floats for each of the count passed, the
        // length of values, from the pointer to the floats of values.
        unsafe { gl::Uniform2fv(location, count, values.as_flattened().as_ptr()) };
        Ok(())
    }

    /// The location of uniform, which has to be one of the program in use.
    fn location_of(&self, uniform: Uniform) -> GLint {
        assert_eq!(uniform.program, self.program, "a uniform of another program");
        uniform.location
    }
}
