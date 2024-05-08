# Conway

Monstrous Software 
May 8, 2024

Experiment to try out compute shaders based on a demo from LWJGL3 based on 
Conway's famous Game of Life.

Keys:
- ESC:   quit
- SPACE: pause
- S:     single step (when paused)

This demo is based on a LWJGL3 demo by Kai Burjack
https://www.youtube.com/watch?v=h7aCroRpkN0
https://github.com/LWJGL/lwjgl3-demos/blob/main/src/org/lwjgl/demo/opengl/shader/GameOfLife.java

The demo was rewritten to make use of standard LibGDX functionality, 
e.g. Texture, Pixmap, InputAdapter, etc.

The compute shader is compiled and linked 'by hand' using GL calls, i.e. not using an (adapted)
LibGDX ShaderProgram.

Other changes: the Texture that is used as input/output buffers for the compute shader are 
in RGBA8888 format, instead of GL_R8UI, so that they can be directly rendered to screen.  
The compute shader was slightly modified for this.   
The downside is the buffers now use 4 floats per pixel instead of one byte.  So it would be interesting
to find a way to make Texture's with single byte "pixels".

Beware: this demo requires the use of a custom LibGDX version that exposes the necessary gl calls that
we need for compute shaders:
*         gl.glBindImageTexture();
*         gl.glDispatchCompute();
*         gl.glMemoryBarrier()

There is a repo by mgsx that does this (among other things) here:
https://github.com/mgsx-dev/libgdx/tree/modern-shaders/compute

