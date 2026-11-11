// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The main function of the behind pass: renderBehind() on the rectangle of the logo, into the
// textures that the logo pass reads it from. A color c is written as c / (1 + c), so every
// brightness of the scene fits into the 0 to 1 of the texture. The coverage of the blue triangle
// goes into the second texture. It follows logo_glass.glsl.
layout(location = 1) out float outCoverage;

void main() {
  setup();
  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
  vec4 behind = renderBehind(CAMERA, normalize(vec3(uv, 2.0)));
  vec3 c = max(behind.rgb, 0.0);
  outColor = vec4(c / (1.0 + c), 1.0);
  outCoverage = behind.a;
}
