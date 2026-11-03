// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// A sprite of the swarm: a soft triangle, or a streak where it moves fast, with a halo,
// premultiplied by its alpha. It follows common.glsl.
in vec2 vLocal;
flat in vec2 vC0;
flat in vec2 vC1;
flat in vec2 vC2;
flat in vec2 vTail;
flat in vec4 vColor;
flat in vec3 vHaloColor;
flat in vec4 vShape;
uniform float uPx;
out vec4 outColor;

void main() {
  float dTri = sdTriangle(vLocal, vC0, vC1, vC2) - vShape.x;
  float tl = dot(vTail, vTail);
  float h = tl > 0.001 ? clamp(dot(vLocal, vTail) / tl, 0.0, 1.0) : 0.0;
  float dSeg = length(vLocal - vTail * h) - vShape.z * (1.0 - 0.6 * h);
  float d = mix(dTri, dSeg, vShape.y);
  float cover = clamp(0.5 - d / uPx, 0.0, 1.0);
  float tailFade = mix(1.0, 1.0 - 0.85 * h, vShape.y);
  float a = vColor.a * cover * tailFade;
  float halo = vShape.w * exp(-max(d, 0.0) / 7.0) * vColor.a * tailFade;
  outColor = vec4(vColor.rgb * a + vHaloColor * halo * (1.0 - cover), a);
}
