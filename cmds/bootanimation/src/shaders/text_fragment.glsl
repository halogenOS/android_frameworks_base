// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// A glyph in the color of its label, covering each pixel as much as the outline of the glyph does.
precision highp float;
in vec2 vTexture;
uniform sampler2D uAtlas;   // the coverage of the glyphs of the label in red
uniform vec4 uColor;        // premultiplied by its alpha
out vec4 outColor;

void main() {
  outColor = uColor * texture(uAtlas, vTexture).r;
}
