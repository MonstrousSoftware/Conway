/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
#version 430 core
#pragma {{DEFINES}}

layout(binding = 0, rgba8) uniform readonly restrict image2D readImage;
layout(binding = 1, rgba8) uniform writeonly restrict image2D writeImage;

#define WS (WX*WY)
#define SW (WX+2u)
#define SH (WY+2u)
#define SZ (SW*SH)

shared vec3 cells[SZ];
layout (local_size_x=WX, local_size_y=WY) in;
void main(void) {
    uvec2 lx = gl_LocalInvocationID.xy;
    for (uint i = WX * lx.y + lx.x; i < SZ; i += WS)
    cells[i] = imageLoad(readImage, ivec2(
        (WX * gl_WorkGroupID.x - 1u) + (i % SW),
        (WY * gl_WorkGroupID.y - 1u) + (i / SW))).rgb;
    barrier();
    #define C(X,Y) int(cells[SW*(lx.y+Y)+lx.x+X].r)
  uint s =  C(1u, 1u);
    uint c =  C(0u, 0u)+C(0u, 1u)+C(0u, 2u)+C(1u, 0u)+
    C(1u, 2u)+C(2u, 0u)+C(2u, 1u)+C(2u, 2u);
    #undef C
  uint r = s == 0u ? uint(c == 3u) : (c == 2u || c == 3u ? 1u : 0u);

    imageStore(writeImage, ivec2(gl_GlobalInvocationID.xy), vec4(r, r, r, 1.0));
}
