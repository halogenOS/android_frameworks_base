// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The glass of the logo, for the behind pass and the logo pass: two glass slabs in the shape of
// the logo triangles, ray marched. The logo starts as dark tinted glass and fills with frosted
// brand glass up to the boot progress. The surface of the fill sways and bubbles rise below it.
// The glass itself does not move, so the logo space is the world moved by CENTER, with no
// rotation, breathing or drift. It follows glass_fragment.glsl.

// Half the thickness of a glass slab, and the radius of the bevel on its faces.
const float PLATE_H = 0.0062;
const float BEVEL = 0.0042;

// The radius of the rounded corners of the glass, in logo sizes.
const float CORNER = 0.11;

// The glass is the logo triangle shrunk towards its incenter by CORNER and rounded by CORNER, which
// gives the outline of the logo triangle with rounded corners. In logo sizes from the home of the
// blue triangle: its tip, its top corner, the length of its slanted sides, its incenter and its
// inradius.
const vec2 TIP = vec2(TRIANGLE_TIP, 0.0);
const vec2 TOP = vec2(TRIANGLE_BACK, TRIANGLE_HALF_HEIGHT);
const float SLANT = length(TOP - TIP);
const float PERIMETER = 2.0 * (SLANT + TRIANGLE_HALF_HEIGHT);
const vec2 INCENTER = vec2(2.0 * (TRIANGLE_HALF_HEIGHT * TRIANGLE_TIP + SLANT * TRIANGLE_BACK) / PERIMETER, 0.0);
const float INRADIUS = 2.0 * (TRIANGLE_BACK - TRIANGLE_TIP) * TRIANGLE_HALF_HEIGHT / PERIMETER;
const float INSET = 1.0 - CORNER / INRADIUS;
const vec2 GLASS_TIP = (INCENTER + (TIP - INCENTER) * INSET) * S;
const vec2 GLASS_TOP = (INCENTER + (TOP - INCENTER) * INSET) * S;
const vec2 GLASS_BOTTOM = vec2(GLASS_TOP.x, -GLASS_TOP.y);

// Left-pointing logo triangle with the corner radius of the glass.
float logoTriangle(vec2 p) {
  return sdTriangle(p, GLASS_TIP, GLASS_BOTTOM, GLASS_TOP) - CORNER * S;
}

// Glass slab in the shape of the triangle, with a rounded bevel on both faces.
float slab(vec3 q, float side) {
  float d2 = logoTriangle(vec2(q.x * side, q.y));
  vec2 w = vec2(d2 + BEVEL, abs(q.z) - PLATE_H + BEVEL);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - BEVEL;
}

vec3 toLogo(vec3 p) { return p - CENTER; }
vec3 piecePos(int id) { return id == 0 ? BLUE_HOME : WHITE_HOME; }
float pieceSide(int id) { return id == 0 ? 1.0 : -1.0; }

// Distance to the outline of a triangle in the plane of the logo, negative inside.
float outline(vec3 lp, int id) {
  vec3 q = lp - piecePos(id);
  return logoTriangle(vec2(q.x * pieceSide(id), q.y));
}

// id 0: blue triangle behind, id 1: light blue-white triangle in front.
float sdPiece(vec3 p, int id) {
  return slab(toLogo(p) - piecePos(id), pieceSide(id));
}

vec3 normalPiece(vec3 p, int id) {
  const vec2 k = vec2(1.0, -1.0) * 0.0005;
  return normalize(k.xyy * sdPiece(p + k.xyy, id) + k.yyx * sdPiece(p + k.yyx, id) +
                   k.yxy * sdPiece(p + k.yxy, id) + k.xxx * sdPiece(p + k.xxx, id));
}

vec2 sphereSpan(vec3 ro, vec3 rd, vec3 c, float r) {
  vec3 oc = ro - c;
  float b = dot(oc, rd);
  float h = b * b - dot(oc, oc) + r * r;
  if (h < 0.0) return vec2(-1.0);
  h = sqrt(h);
  return vec2(-b - h, -b + h);
}

// The most steps that a ray takes towards the glass. Rays that pass close along a slab need the
// most, and a ray that has not hit the glass after this many misses it.
const int MARCH_STEPS = 90;
// How close to the glass a step of a ray ends for the ray to hit it.
const float HIT_DISTANCE = 0.0003;

// x: distance along the ray, or -1 for a miss; y: id of the piece hit.
vec2 march(vec3 ro, vec3 rd, bool withWhite) {
  vec2 span = sphereSpan(ro, rd, CENTER, BOUND);
  if (span.y <= 0.0) return vec2(-1.0);
  float t = max(span.x, 0.0);
  for (int i = 0; i < MARCH_STEPS; i++) {
    if (t > span.y) break;
    vec3 p = ro + rd * t;
    float db = sdPiece(p, 0);
    float dw = withWhite ? sdPiece(p, 1) : 1e9;
    float d = min(db, dw);
    if (d < HIT_DISTANCE) return vec2(t, dw < db ? 1.0 : 0.0);
    t += d;
  }
  return vec2(-1.0);
}

// Position from the tip of the triangle to its back, 0 to 1; tiles land and leave in this order.
float fromTip(vec3 lp, int id) {
  float x = (lp - piecePos(id)).x * pieceSide(id);
  return sat((x - TRIANGLE_TIP * S) / ((TRIANGLE_BACK - TRIANGLE_TIP) * S));
}

// How far the glass has set at this point: it sets right behind the landing tiles
// and breaks up again just before the tiles leave.
float setAt(float f) {
  float r = smooth01((uTime - 1.63 - 1.1 * f) / 0.3);
  return r * (1.0 - smooth01((uSinceExit - 0.45 - 0.55 * f) / 0.2));
}

// Lighting of the glass, in linear color. Its world looks along -z, so z flips.
vec3 meshSpace(vec3 v) { return vec3(v.x, v.y, -v.z); }
vec3 lin(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 studio(vec3 d) {
  vec3 c = vec3(0.004, 0.006, 0.012) + vec3(0.02, 0.03, 0.05) * max(d.z, 0.0);
  float dk = dot(d, normalize(vec3(-0.55, 0.72, 0.6)));
  c += vec3(1.0, 0.90, 0.78) * (2.6 * smoothstep(0.86, 0.975, dk) + 0.22 * pow(max(dk, 0.0), 6.0));
  float dr = dot(d, normalize(vec3(1.0, 0.18, -0.2)));
  c += vec3(0.10, 0.62, 1.0) * (2.2 * smoothstep(0.80, 0.96, dr) + 0.15 * pow(max(dr, 0.0), 4.0));
  c += vec3(0.75, 0.88, 1.0) * 1.3 * smoothstep(0.93, 0.99, 1.0 - abs(d.x)) * smoothstep(0.25, 0.7, d.y);
  c += vec3(0.01, 0.05, 0.16) * smoothstep(0.1, -0.9, d.y);
  return c;
}
vec3 tonemap(vec3 x) { vec3 s = 0.78 + 0.22 * (1.0 - exp(-(x - 0.78) / 0.22)); return mix(x, s, step(0.78, x)); }
vec3 toDisplay(vec3 c) { return pow(clamp(tonemap(max(c, 0.0)), 0.0, 1.0), vec3(1.0 / 2.2)); }

// Brand gradient across a triangle, from the top left to the bottom right.
float gradientAt(vec3 lp, int id) {
  vec2 q = (lp - piecePos(id)).xy;
  float left = id == 0 ? TRIANGLE_TIP : -TRIANGLE_BACK;
  float across = (q.x / S - left) / (TRIANGLE_BACK - TRIANGLE_TIP);
  float down = (TRIANGLE_HALF_HEIGHT - q.y / S) / (2.0 * TRIANGLE_HALF_HEIGHT);
  return sat((across + down) * 0.5);
}

struct Surface { vec3 lp; vec3 n; vec3 N; vec3 R; float F; float edge; float t; };

Surface surfaceAt(vec3 p, vec3 d, int id) {
  Surface s;
  s.n = normalPiece(p, id);
  s.lp = toLogo(p);
  s.edge = sat(1.0 - abs(s.n.z));   // 0 on the flat face, 1 on the side of the slab
  s.N = meshSpace(s.n);
  vec3 V = meshSpace(-d);
  float ndv = sat(dot(s.N, V));
  s.F = 0.04 + 0.96 * pow(1.0 - ndv, 5.0);
  s.R = reflect(-V, s.N);
  s.t = gradientAt(s.lp, id);
  return s;
}

// Ribbon and halo seen through the blue triangle, in linear color.
vec3 backdrop(vec2 uv) { return lin(background(CAMERA, normalize(vec3(uv, 2.0)))); }

// Height of the fill at x in the logo. It sways while the boot runs and rests when empty or full.
float fillLevel(float x) {
  float sway = 0.022 * S * sin(x / S * 7.0 - uTime * 2.1) + 0.012 * S * sin(x / S * 3.0 + uTime * 1.3);
  float still = smoothstep(0.0, 0.05, uProgress) * (1.0 - smoothstep(0.97, 1.0, uProgress));
  return mix(-0.74 * S, 0.74 * S, uProgress) + sway * still;
}

float filled(vec3 lp) { return smoothstep(-0.0008, 0.0008, fillLevel(lp.x) - lp.y); }

float meniscus(vec3 lp) {
  float line = exp(-pow((fillLevel(lp.x) - lp.y) / 0.0011, 2.0));
  return line * smoothstep(0.0, 0.03, uProgress) * (1.0 - smoothstep(0.97, 1.0, uProgress));
}

float bubbles(vec2 q) {
  vec2 g = q / (0.11 * S) - vec2(0.0, uTime * 1.6);
  vec2 c = floor(g);
  if (hash(c) < 0.82) return 0.0;
  vec2 f = fract(g) - 0.5 - (vec2(hash(c + 3.1), hash(c + 7.7)) - 0.5) * 0.5;
  return smoothstep(0.16, 0.07, length(f));
}

vec3 shadeBlue(vec3 p, vec3 d, vec2 uv) {
  Surface s = surfaceAt(p, d, 0);
  vec3 base = mix(lin(BLUE_A), lin(BLUE_B), s.t);
  float rim = exp(outline(s.lp, 0) / 0.006);
  vec3 empty = backdrop(uv - s.n.xy * 0.02) * base * 1.6 + base * 0.05 + base * (rim + s.edge) * 0.3;
  empty += studio(s.R) * s.F * 0.4;
  float wrap = sat(dot(s.N, normalize(vec3(-0.55, 0.72, 0.6))) * 0.5 + 0.5);
  vec3 full = base * (0.38 + 0.48 * wrap + 0.14 * (1.0 - s.t)) + base * s.edge * 0.45;
  full += studio(s.R) * mix(s.F, 1.0, 0.12 * s.edge);
  float f = filled(s.lp);
  vec3 col = mix(empty, full, f) + LIGHT * (0.35 * bubbles(s.lp.xy) * f + 1.1 * meniscus(s.lp));
  return toDisplay(col);
}

// Everything except the front triangle: ribbon, halo and the blue triangle, as far as it has set.
vec3 renderBehind(vec3 ro, vec3 rd) {
  vec3 bg = background(ro, rd);
  vec2 h = march(ro, rd, false);
  if (h.x < 0.0) return bg;
  vec3 p = ro + rd * h.x;
  float r = setAt(fromTip(toLogo(p), 0));
  if (r <= 0.0) return bg;
  vec3 c = shadeBlue(p, rd, rd.xy * 2.0 / rd.z) + LIGHT * 0.45 * 4.0 * r * (1.0 - r);
  return mix(bg, c, r);
}
