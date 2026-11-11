// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The main function of the glass passes that draw into the window: what render() of the pass
// returns, with the progress line over it, a soft clip of bright colors and dither. The alpha of a
// pixel is how much the black behind the scene and the glass of the logo cover it. The window shows
// what the system draws below through the rest. render() returns the colors of the glass
// premultiplied by their cover. The ribbon, the halo and the progress line add their light to
// what shows through. It follows the part of the pass that defines render().
void main() {
  setup();
  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
  vec4 scene = render(CAMERA, normalize(vec3(uv, 2.0)), uv);
  vec3 col = progressLine(uv, scene.rgb);
  vec3 over = max(col - 0.8, 0.0);
  col = min(col, 0.8) + 0.2 * (1.0 - exp(-over / 0.2));
  float cover = mix(uBlackOpacity, 1.0, scene.a);
  // Scaling the dither by the cover sets a see-through pixel without light to exactly 0.
  col += cover * (hash(gl_FragCoord.xy + fract(uTime)) - 0.5) / 255.0;
  outColor = vec4(max(col, 0.0), cover);
}
