// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

// render() of the pass over the whole window: the ribbon and the halo without the logo, which the
// logo pass draws over them.
vec3 render(vec3 ro, vec3 rd, vec2 uv) { return background(ro, rd); }
