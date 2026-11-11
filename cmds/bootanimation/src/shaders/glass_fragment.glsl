// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The fragment shader of the Glass fill scene, shared by its passes: the glass ribbon behind the
// logo, the halo around it and the progress line. It follows common.glsl and glass_space.glsl.

uniform vec2 uRes;
uniform float uTime;      // seconds since the animation started
uniform float uSinceExit; // seconds since the exit started, 0 until it starts
uniform float uProgress;  // boot progress, 0 to 1
uniform vec2 uBar;        // progress line: vertical position and half width, in screen heights
uniform float uLineOpacity; // opacity of the progress line, 0 to 1
out vec4 outColor;

const vec3 GLOW    = vec3(0.000, 0.706, 1.000);
const vec3 LIGHT   = vec3(0.86, 0.95, 1.00);

// Animation state, computed by setup().
float gReveal, gGlow;

float sat(float x) { return clamp(x, 0.0, 1.0); }
float smooth01(float x) { x = sat(x); return x * x * (3.0 - 2.0 * x); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void setup() {
  float T = uTime;
  float E = uSinceExit;
  gReveal = smooth01((T - 1.7) / 2.2) * (1.0 - smooth01((E - 0.3) / 1.8));
  gGlow = smooth01((T - 1.4) / 1.4) * (0.33 + 0.1 * sin(2.0 * PI * T / 3.2)) * (1.0 - smooth01(E / 1.4));
}

// Half the width of the ribbon at a twist, where its edges cross; in screen units.
const float MIN_HALF_WIDTH = 0.009;

// How far around a twist, in units of the twist cosine, the two faces of the ribbon blend.
const float TWIST_BLEND = 0.3;

// The body and the edge lights of one face of the ribbon; side runs from -1 at the dim edge to 1
// at the lit edge.
vec3 ribbonFace(float side, float inside) {
  vec3 c = mix(vec3(0.004, 0.02, 0.06), vec3(0.012, 0.06, 0.15), 0.5 + 0.5 * side) * inside;
  c += mix(BLUE_A, LIGHT, 0.35) * exp(-pow((side - 0.9) / 0.05, 2.0)) * 0.6;
  c += BLUE_A * exp(-pow((side + 0.9) / 0.04, 2.0)) * 0.22;
  c += LIGHT * exp(-pow((side - 0.45) / 0.14, 2.0)) * 0.05 * inside;
  return c;
}

// The glass ribbon behind the logo, drawn on a plane; q is in screen units.
vec3 ribbon(vec2 q) {
  if (gReveal <= 0.001) return vec3(0.0);
  float T = uTime;
  float y = q.y - CENTER.y;
  float dy = abs(y);
  float reach = gReveal * 0.8;
  float mask = smoothstep(reach, reach - 0.2, dy) * exp(-dy * dy * 6.0);
  mask *= 1.0 - 0.7 * exp(-pow((q.y - uBar.x - 0.01) / 0.045, 2.0));   // keep the progress text readable
  if (mask <= 0.0) return vec3(0.0);

  float wave = 0.3 * sin(T * 0.29);
  float xc  = -0.24 * y + 0.055 * sin(y * 5.0 + 0.9 + wave);
  float dxc = -0.24 + 0.275 * cos(y * 5.0 + 0.9 + wave);
  float tw = cos(y * 3.4 + 0.6 - T * 0.25);
  float w = 0.072 * tw;
  float u = (q.x - xc) * inversesqrt(1.0 + dxc * dxc) * inversesqrt(w * w + MIN_HALF_WIDTH * MIN_HALF_WIDTH);
  float au = abs(u);

  vec3 c = vec3(0.0);
  float x2 = xc + 0.1 * sin(y * 2.6 + 1.4 + 0.5 * wave);
  float flow2 = 0.25 + 0.75 * pow(0.5 + 0.5 * sin(y * 6.0 - T * 1.7), 6.0);
  c += mix(BLUE_A, LIGHT, 0.3) * exp(-pow((q.x - x2) / 0.0022, 2.0)) * 0.16 * flow2;
  c += GLOW * exp(-abs(q.x - x2) / 0.012) * 0.025 * flow2;
  if (au > 3.0) return c * mask * sqrt(gReveal);

  float inside = 1.0 - smoothstep(0.96, 1.04, au);
  // Near a twist the band shows both of its faces at once, so the lit edge changes sides gradually.
  float facing = smoothstep(-TWIST_BLEND, TWIST_BLEND, tw);
  c += mix(ribbonFace(-u, inside), ribbonFace(u, inside), facing);
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    float line = exp(-pow((u - (-0.5 + 0.5 * fi)) / 0.05, 2.0));
    float flow = pow(0.5 + 0.5 * sin(y * 9.0 - T * 2.2 + fi * 2.1), 10.0);
    c += mix(BLUE_A, WHITE_A, 0.6) * line * (0.04 + flow) * 0.32 * inside;
  }
  c += GLOW * exp(-max(au - 1.0, 0.0) * 12.0) * (1.0 - inside) * 0.05;
  c *= mix(1.8, 1.0, abs(tw));
  return c * mask * sqrt(gReveal);
}

vec3 background(vec3 o, vec3 d) {
  if (d.z < 0.05) return vec3(0.0);
  vec2 q = (o.xy + d.xy * (PLANE_Z - o.z) / d.z) * (2.0 / (2.0 + PLANE_Z));
  float r = length(q - CENTER.xy);
  vec3 halo = GLOW * gGlow * (0.16 * exp(-r * r * 90.0) + 0.05 * exp(-r * 13.0));
  return ribbon(q) + halo;
}

// Progress line: a faint track, a fill that runs from deep blue to blue-white, a soft light at its head.
vec3 progressLine(vec2 uv, vec3 col) {
  float px = 1.0 / uRes.y;
  float a = uLineOpacity;
  float shrink = smooth01(uSinceExit / 0.55);
  if (a <= 0.0) return col;
  vec2 p = uv - vec2(0.0, uBar.x);
  float hw = uBar.y * (1.0 - shrink);
  float th = max(0.0009, 0.75 * px);
  float dist = length(vec2(max(abs(p.x) - hw, 0.0), p.y)) - th;
  float cover = sat(0.5 - dist / px);
  col += vec3(0.75, 0.85, 1.0) * 0.12 * cover * a;

  float x0 = -hw, x1 = -hw + 2.0 * hw * uProgress;
  float fill = cover * sat((x1 - p.x) / px + 0.5);
  float s = sat((p.x - x0) / max(x1 - x0, 1e-4));
  vec3 fc = mix(BLUE_B, BLUE_A, smoothstep(0.0, 0.7, s));
  fc = mix(fc, WHITE_A, smoothstep(0.7, 1.0, s) * 0.85);
  float sweep = fract(uTime / 2.2) * 1.4 - 0.2;
  fc += LIGHT * 0.5 * exp(-pow((s - sweep) / 0.07, 2.0));
  col = mix(col, fc, fill * a);
  if (uProgress > 0.001) {
    vec2 hp = p - vec2(x1, 0.0);
    col += GLOW * a * (exp(-dot(hp, hp) / 0.00004) * 0.55 + exp(-dot(hp, hp) / 0.0004) * 0.12);
    col += GLOW * a * 0.07 * exp(-abs(p.y) / 0.005) * step(x0, p.x) * step(p.x, x1);
  }
  return col;
}
