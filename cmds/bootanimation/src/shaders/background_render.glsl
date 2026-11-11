// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// render() of the pass over the whole window: the ribbon and the halo without the logo, which the
// logo pass draws over them. Only the logo pass has glass that covers pixels.
vec4 render(vec3 ro, vec3 rd, vec2 uv) { return vec4(background(ro, rd), 0.0); }
