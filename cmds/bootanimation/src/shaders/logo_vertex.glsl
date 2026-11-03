// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The rectangle on the window that contains the glass of the logo, for the passes that shade it:
// the bounds of the corners of the cube around the sphere of BOUND, as the camera sees them. The
// cube lies in front of the camera, so the sphere inside it shows inside these bounds.
layout(location = 0) in vec2 aCorner;   // corner of the rectangle, -1 to 1
uniform vec2 uRes;
flat out vec4 vLogoRect;                // lower left and upper right corner of the rectangle

void main() {
  vec2 lo = vec2(1e9);
  vec2 hi = vec2(-1e9);
  for (int i = 0; i < 8; i++) {
    vec3 corner = CENTER + BOUND * (2.0 * vec3(i & 1, (i >> 1) & 1, (i >> 2) & 1) - 1.0);
    vec2 uv = 2.0 * (corner.xy - CAMERA.xy) / (corner.z - CAMERA.z);
    lo = min(lo, uv);
    hi = max(hi, uv);
  }
  vLogoRect = vec4(lo, hi);
  vec2 uv = mix(lo, hi, 0.5 * aCorner + 0.5);
  gl_Position = vec4(2.0 * uv * uRes.y / uRes, 0.0, 1.0);
}
