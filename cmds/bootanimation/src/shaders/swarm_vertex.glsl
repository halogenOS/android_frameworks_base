// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// The swarm of the Glass fill scene: tiles that fly in from their stars to build the logo and
// carry it away at the exit, dust that drifts down and swirls around the logo, and stardust that
// drifts off the head of the progress line. Each sprite is a quad around a soft triangle with a
// streak along its motion. Positions are in device pixels, the pixels of a 1080 x 2400 screen,
// from the top left corner, y down. The window is DEVICE_HEIGHT of them high. It follows common.glsl and the constants written by the Rust code of the scene.
layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec4 aPlace;   // home x, home y, star x, star y
layout(location = 2) in vec4 aFlight;  // curve control x, curve control y, delay, duration
layout(location = 3) in vec4 aKind;    // kind, orientation, gradient position, order from the tip
layout(location = 4) in vec4 aSeed;

uniform float uTime;
uniform float uSinceExit;   // seconds since the exit started, 0 until it starts
uniform float uProgress;
uniform float uPx;          // device pixels per window pixel
uniform vec2 uDevice;       // the window in device pixels
uniform vec2 uTri[6];       // the corners of a tile of the blue, then of the white triangle
uniform vec3 uLogo;         // center x, center y and size of the logo
uniform vec3 uBarEnds;      // left end x, right end x and y of the progress line

out vec2 vLocal;
flat out vec2 vC0;
flat out vec2 vC1;
flat out vec2 vC2;
flat out vec2 vTail;
flat out vec4 vColor;
flat out vec3 vHaloColor;
flat out vec4 vShape;

// The time between the two states of a sprite that its velocity is measured from, in seconds.
// The velocity sets the length of the streak, so the streak is the same at every frame rate.
const float VELOCITY_STEP = 1.0 / 60.0;
// A speed above this, in device pixels per second, is a jump of dust that wraps around the
// window, which is drawn without a streak.
const float WRAP_SPEED = 6000.0;

struct State { vec2 pos; float rot; float scale; float alpha; float heat; float settle; };

vec2 rotate(vec2 p, float a) {
  float c = cos(a), s = sin(a);
  return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float smoother(float x) {
  x = clamp(x, 0.0, 1.0);
  return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
}

// Seconds since the exit started at the moment t, 0 until it starts.
float sinceExitAt(float t) {
  return max(uSinceExit - (uTime - t), 0.0);
}

State tile(float t) {
  State s;
  vec2 c = uLogo.xy;
  float p = clamp((t - aFlight.z) / aFlight.w, 0.0, 1.0);
  float e = smoother(p);
  float ie = 1.0 - e;
  vec2 star = aPlace.zw + vec2(sin(t * 0.7 + aSeed.x * TAU), cos(t * 0.6 + aSeed.y * TAU)) * 5.0;
  s.pos = ie * ie * star + 2.0 * ie * e * aFlight.xy + e * e * aPlace.xy;
  s.rot = (aSeed.z - 0.5) * 7.0 * ie;
  s.scale = mix(0.32, 1.0, e);
  float twinkle = 0.6 + 0.4 * sin(t * (2.0 + 3.0 * aSeed.x) + aSeed.y * TAU);
  float starAlpha = smoothstep(0.0, 0.8, t) * 0.6 * twinkle;
  s.alpha = mix(starAlpha, 1.0, smoothstep(0.0, 0.35, p));
  s.settle = smoothstep(0.55, 1.0, p);
  s.heat = sin(PI * p) * 0.9;

  // The glass sets where a tile has landed, so the tile fades; it comes back just before it leaves.
  float landed = smoothstep(0.05, 0.35, t - aFlight.z - aFlight.w);
  float ex = sinceExitAt(t) - LEAVE_DELAY - (aKind.w * 0.55 + aSeed.y * 0.15);
  s.alpha *= max(1.0 - landed, smoothstep(-0.3, -0.05, ex));

  if (ex > -0.5) {
    float side = int(aKind.x) == KIND_BLUE_TILE ? -1.0 : 1.0;
    vec2 dir = normalize(vec2(side, (aPlace.y - c.y) / uLogo.z * 0.6 + (aSeed.x - 0.5) * 0.4));
    float q = max(ex, 0.0);
    float dist = 110.0 * q + 980.0 * q * q;
    s.pos += rotate(dir, -(0.8 + 0.7 * aSeed.z) * q) * dist;
    s.alpha *= 1.0 - smoothstep(0.25, 1.2, q);
    s.scale *= 1.0 - 0.4 * smoothstep(0.0, 1.0, q);
    s.heat += 0.45 * smoothstep(-0.5, 0.0, ex) * (1.0 - smoothstep(0.0, 0.5, q));
  }
  return s;
}

State dust(float t) {
  State s;
  vec2 c = uLogo.xy;
  float speed = 5.0 + 16.0 * aSeed.x;
  vec2 p = aPlace.xy + vec2(sin(t * 0.11 + aSeed.y * TAU) * 14.0, -t * speed);
  p.y = mod(p.y + DUST_OVERHANG_Y, uDevice.y + 2.0 * DUST_OVERHANG_Y) - DUST_OVERHANG_Y;
  vec2 r = p - c;
  float swirl = -0.55 * smoothstep(0.1, 3.2, t) / (1.0 + length(r) / 450.0);
  p = c + rotate(r, swirl);
  float sinceExit = sinceExitAt(t);
  float q = max(sinceExit - LEAVE_DELAY, 0.0);
  p += normalize(p - c + 0.001) * (60.0 * q + 320.0 * q * q);
  s.pos = p;
  s.rot = aSeed.z * TAU + t * (aSeed.x - 0.5) * 0.8;
  s.scale = 0.2 + 0.3 * aSeed.y;
  float twinkle = 0.55 + 0.45 * sin(t * (0.8 + 2.2 * aSeed.y) + aSeed.z * TAU);
  float present = 1.0 - smoothstep(2.4, 4.0, t) + smoothstep(LEAVE_DELAY - 0.6, LEAVE_DELAY, sinceExit);
  s.alpha = (0.10 + 0.6 * pow(aSeed.z, 4.0)) * twinkle * smoothstep(0.0, 1.2, t) * present;
  s.heat = 0.0;
  s.settle = 1.0;
  return s;
}

// Stardust drifting off the head of the progress line.
State spark(float t) {
  State s;
  float life = fract(t * 0.9 + aSeed.x);
  float head = mix(uBarEnds.x, uBarEnds.y, uProgress);
  s.pos = vec2(head - life * (14.0 + 46.0 * aSeed.y),
               uBarEnds.z - life * (22.0 + 78.0 * aSeed.z) + sin(life * 6.0 + aSeed.x * 9.0) * 5.0);
  s.rot = aSeed.y * TAU + life * 3.0;
  s.scale = (0.16 + 0.12 * aSeed.z) * (1.0 - 0.5 * life);
  float on = smoothstep(2.2, 2.9, t) * (1.0 - smoothstep(0.985, 1.0, uProgress))
           * (1.0 - smoothstep(0.1, 0.5, sinceExitAt(t)));
  s.alpha = (1.0 - life) * (1.0 - life) * 0.75 * on;
  s.heat = 0.3;
  s.settle = 1.0;
  return s;
}

vec4 toClip(vec2 p) {
  vec2 q = 2.0 * p / uDevice - 1.0;
  return vec4(q.x, -q.y, 0.0, 1.0);
}

void main() {
  int kind = int(aKind.x);
  float fade = 1.0 - smoothstep(LEAVE_DELAY + 0.7, LEAVE_DELAY + 2.0, uSinceExit);
  vC0 = vC1 = vC2 = vTail = vec2(0.0);
  vShape = vec4(0.0);
  vHaloColor = vec3(0.0);

  State s;
  State prev;
  if (kind == KIND_BLUE_TILE || kind == KIND_WHITE_TILE) { s = tile(uTime); prev = tile(uTime - VELOCITY_STEP); }
  else if (kind == KIND_DUST) { s = dust(uTime); prev = dust(uTime - VELOCITY_STEP); }
  else { s = spark(uTime); prev = spark(uTime - VELOCITY_STEP); }

  float alpha = s.alpha * fade;
  if (alpha < 0.003) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }

  vec2 vel = (s.pos - prev.pos) / VELOCITY_STEP;
  float speed = length(vel);
  if (speed > WRAP_SPEED) speed = 0.0;
  vec2 dir = speed > 1.0 ? vel / speed : vec2(1.0, 0.0);
  float streak = kind == KIND_SPARK ? 0.0 : smoothstep(140.0, 700.0, speed);
  float len = min(speed * 0.055, 170.0) * streak;

  bool white = kind == KIND_WHITE_TILE;
  vec2 o0 = white ? uTri[3] : uTri[0];
  vec2 o1 = white ? uTri[4] : uTri[1];
  vec2 o2 = white ? uTri[5] : uTri[2];
  vec2 shape = vec2(aKind.y);
  float k = s.scale * 0.9;
  o0 = rotate(o0 * shape, s.rot) * k;
  o1 = rotate(o1 * shape, s.rot) * k;
  o2 = rotate(o2 * shape, s.rot) * k;
  float reach = max(max(length(o0), length(o1)), length(o2));
  float inner = 0.48 * length(uTri[0]) * k;
  vC0 = o0 * 0.78;
  vC1 = o1 * 0.78;
  vC2 = o2 * 0.78;

  vec3 col;
  vec3 halo;
  float haloStrength;
  if (kind == KIND_BLUE_TILE) {
    col = mix(BLUE_A, BLUE_B, aKind.z) * (0.86 + 0.26 * aSeed.x);
    halo = vec3(0.0, 0.55, 1.0);
    haloStrength = 0.11;
  } else if (white) {
    col = mix(WHITE_A, WHITE_B, aKind.z) * (0.93 + 0.12 * aSeed.x);
    alpha *= mix(0.9, 0.64, s.settle) + 0.25 * clamp(s.heat, 0.0, 1.0) * s.settle;
    halo = vec3(0.45, 0.78, 1.0);
    haloStrength = 0.1;
  } else if (kind == KIND_DUST) {
    col = aSeed.w > 0.8 ? vec3(0.78, 0.9, 1.0) : mix(vec3(0.0, 0.40, 0.66), vec3(0.0, 0.71, 0.91), aSeed.w / 0.8);
    halo = col;
    haloStrength = 0.35;
  } else {
    col = vec3(0.8, 0.93, 1.0);
    halo = vec3(0.2, 0.7, 1.0);
    haloStrength = 0.22;
  }
  col = mix(col, vec3(0.86, 0.96, 1.0), clamp(s.heat * 0.5, 0.0, 0.8));
  haloStrength *= 1.0 + 2.5 * clamp(s.heat, 0.0, 1.5);

  float R = reach + 18.0;
  float along = aCorner.x < 0.0 ? -(len + R) : R;
  vec2 across = vec2(-dir.y, dir.x);
  vLocal = dir * along + across * (aCorner.y * R);
  vTail = -dir * len;
  vColor = vec4(col, alpha);
  vHaloColor = halo;
  vShape = vec4(0.22 * inner, streak, max(0.3 * reach, 1.2 * uPx), haloStrength);
  gl_Position = toClip(s.pos + vLocal);
}
