/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
#version 430 core
#pragma {{DEFINES}}

layout (local_size_x=WX, local_size_y=WY) in;

layout(binding = 0, rgba8) uniform readonly restrict image2D readImage;
layout(binding = 1, rgba8) uniform writeonly restrict image2D writeImage;

#define WS (WX*WY)
#define SW (WX+2u)
#define SH (WY+2u)
#define SZ (SW*SH)

shared vec3 cells[SZ];  // cell matrix shared with the work group
// note that the matrix has a border of one cell around the work group size
// because we need the neighbour cells from the adjacent work groups.

void main(void) {
    uvec2 lx = gl_LocalInvocationID.xy; // relative position in work group

    for (uint i = WX * lx.y + lx.x; i < SZ; i += WS) {
        cells[i] = imageLoad(readImage, ivec2(
            (WX * gl_WorkGroupID.x - 1u) + (i % SW),
            (WY * gl_WorkGroupID.y - 1u) + (i / SW))).rgb;
    }
    barrier(); // wait till whole work group reaches this point, so cells[] is filled

    #define C(X,Y) int(cells[SW*(lx.y+Y)+lx.x+X].r)
    uint s =  C(1u, 1u);    // centre cell

    // count of neigbouring cells that are alive
    uint c =  C(0u, 0u)+C(0u, 1u)+C(0u, 2u)+C(1u, 0u)+
    C(1u, 2u)+C(2u, 0u)+C(2u, 1u)+C(2u, 2u);
    #undef C
    uint r = s == 0u ? uint(c == 3u) : (c == 2u || c == 3u ? 1u : 0u);

    // write as a black or white pixel
    imageStore(writeImage, ivec2(gl_GlobalInvocationID.xy), vec4(r, r, r, 1.0));
}
