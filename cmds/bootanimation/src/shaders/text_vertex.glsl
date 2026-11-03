// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// A glyph of a label: a quad on the pixels of the window that its coverage covers, reading the
// coverage from its place in the texture of the label. Places on the window are in pixels from the
// top left corner, y down.
layout(location = 0) in vec2 aCorner;   // corner of the quad, -1 to 1
layout(location = 1) in vec4 aPlace;    // left, top, right and bottom on the window
layout(location = 2) in vec4 aTexture;  // left, top, right and bottom in the texture, 0 to 1
uniform vec2 uRes;

out vec2 vTexture;

void main() {
  vec2 along = 0.5 * aCorner + 0.5;
  vec2 pixel = mix(aPlace.xy, aPlace.zw, along);
  vTexture = mix(aTexture.xy, aTexture.zw, along);
  gl_Position = vec4(2.0 * pixel.x / uRes.x - 1.0, 1.0 - 2.0 * pixel.y / uRes.y, 0.0, 1.0);
}
