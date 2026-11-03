// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// render() of the pass over the logo: the light blue-white glass of the front triangle over the
// scene behind it. The behind pass draws that scene into uBehind before this pass, so each pixel
// reads it from there, once for itself and once for each sample of the blur seen through the
// front triangle. It follows logo_glass.glsl.

flat in vec4 vLogoRect;            // the rectangle of the pass: lower left and upper right corner
uniform highp sampler2D uBehind;   // renderBehind() on the rectangle, from behind_fragment.glsl

// renderBehind() at the screen point q. The ray through a point within one pixel of the edge of
// the rectangle, or outside it, misses the sphere around the glass, so renderBehind() returns the
// background there.
vec3 behindAt(vec2 q) {
  vec2 pixel = vec2(1.0 / uRes.y);
  if (any(lessThan(q, vLogoRect.xy + pixel)) || any(greaterThan(q, vLogoRect.zw - pixel))) {
    return background(CAMERA, normalize(vec3(q, 2.0)));
  }
  vec3 stored = texture(uBehind, (q * uRes.y + 0.5 * uRes) / uRes).rgb;
  return stored / (1.0 - stored);
}

// The samples of the blur, on a spiral turning by the golden angle from one to the next.
const int BLUR_SAMPLES = 10;
const float GOLDEN_ANGLE = PI * (3.0 - sqrt(5.0));

// The scene behind the front triangle in linear color, blurred over a disk with a fixed spiral of samples.
vec3 seenBehind(vec2 uv, float radius) {
  vec3 sum = vec3(0.0);
  for (int k = 0; k < BLUR_SAMPLES; k++) {
    float fk = float(k) + 0.5;
    float a = fk * GOLDEN_ANGLE;
    vec2 q = uv + vec2(cos(a), sin(a)) * radius * sqrt(fk / float(BLUR_SAMPLES));
    sum += lin(behindAt(q));
  }
  return sum / float(BLUR_SAMPLES);
}

vec3 shadeWhite(vec3 p, vec3 d, vec2 uv) {
  Surface s = surfaceAt(p, d, 1);
  vec3 tint = mix(lin(WHITE_A), lin(WHITE_B), s.t);
  float rim = exp(outline(s.lp, 1) / 0.006);
  float f = filled(s.lp);
  vec3 bg = seenBehind(uv - s.n.xy * mix(0.012, 0.010 + 0.05 * s.edge, f), mix(0.007, 0.012, f));
  vec3 empty = bg * (0.5 + 0.35 * tint) + tint * 0.04 + tint * (rim + s.edge) * 0.25 + studio(s.R) * s.F * 0.4;
  vec3 full = (bg * (0.45 + 0.4 * tint) + tint * (0.40 + 0.14 * (1.0 - s.t))) * (1.0 - s.F);
  full += studio(s.R) * s.F + tint * s.edge * 0.22;
  vec3 col = mix(empty, full, f) + LIGHT * (0.35 * bubbles(s.lp.xy) * f + 1.3 * meniscus(s.lp));
  return toDisplay(col);
}

vec3 render(vec3 ro, vec3 rd, vec2 uv) {
  vec2 h = march(ro, rd, true);
  if (h.x < 0.0 || h.y < 0.5) return behindAt(uv);
  vec3 p = ro + rd * h.x;
  float r = setAt(fromTip(toLogo(p), 1));
  if (r <= 0.0) return behindAt(uv);
  vec3 c = shadeWhite(p, rd, uv) + LIGHT * 0.45 * 4.0 * r * (1.0 - r);
  return r >= 0.999 ? c : mix(behindAt(uv), c, r);
}
