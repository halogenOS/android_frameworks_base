// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The space of the Glass fill scene, shared by the vertex and fragment shaders of its passes.
// Screen units: the screen is 1 high and as wide as its aspect, the origin is its center. A ray
// from CAMERA through the screen point uv runs along vec3(uv, 2.0). It follows common.glsl and the
// constants written by the Rust code of the scene.

const float S = LOGO_SIZE;
const vec3 CENTER = vec3(0.0, LOGO_CENTER_Y, 0.0);
const float PLANE_Z = 0.25;
const vec3 BLUE_HOME = vec3(0.5 * TRIANGLE_OVERLAP * S, 0.0, 0.021);
const vec3 WHITE_HOME = vec3(-0.5 * TRIANGLE_OVERLAP * S, 0.0, -0.021);
const vec3 CAMERA = vec3(0.0, 0.0, -2.0);

// How far the farthest corner of a logo triangle lies from the home of the triangle.
const float TRIANGLE_REACH = max(-TRIANGLE_TIP, length(vec2(TRIANGLE_BACK, TRIANGLE_HALF_HEIGHT))) * S;
// Radius of the sphere around CENTER that contains the glass of the logo, with 0.02 to spare for
// the thickness of the glass. The glass does not move.
const float BOUND = length(BLUE_HOME) + TRIANGLE_REACH + 0.02;
