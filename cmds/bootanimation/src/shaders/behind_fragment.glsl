// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The main function of the behind pass: renderBehind() on the rectangle of the logo, into the
// texture that the logo pass reads it from. A color c is written as c / (1 + c), so every
// brightness of the scene fits into the 0 to 1 of the texture. It follows logo_glass.glsl.
void main() {
  setup();
  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
  vec3 c = max(renderBehind(CAMERA, normalize(vec3(uv, 2.0))), 0.0);
  outColor = vec4(c / (1.0 + c), 1.0);
}
