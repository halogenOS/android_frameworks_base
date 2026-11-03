// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The main function of the glass passes that draw into the window: what render() of the pass
// returns, with the progress line over it, a soft clip of bright colors and dither. It follows
// the part of the pass that defines render().
void main() {
  setup();
  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
  vec3 col = render(CAMERA, normalize(vec3(uv, 2.0)), uv);
  col = progressLine(uv, col);
  vec3 over = max(col - 0.8, 0.0);
  col = min(col, 0.8) + 0.2 * (1.0 - exp(-over / 0.2));
  col += (hash(gl_FragCoord.xy + fract(uTime)) - 0.5) / 255.0;
  outColor = vec4(max(col, 0.0), 1.0);
}
