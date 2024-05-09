# Conway

Monstrous Software 
May 8, 2024

Experiment to try out compute shaders based on a demo from LWJGL3 based on 
Conway's famous Game of Life.
![screenshot](https://github.com/MonstrousSoftware/Conway/assets/49096535/74289931-1653-49e2-8660-fb1e45da6d61)

Keys:
- ESC:   quit
- SPACE: pause
- S:     single step (when paused)
- scroll wheel : zoom
- mouse drag : move view

Requires GLES 3.1 profile (OpenGL 4.3).

This demo is based on a LWJGL3 demo by Kai Burjack
https://www.youtube.com/watch?v=h7aCroRpkN0
https://github.com/LWJGL/lwjgl3-demos/blob/main/src/org/lwjgl/demo/opengl/shader/GameOfLife.java

The demo was adapted to make use of standard LibGDX functionality, 
e.g. Texture, Pixmap, InputAdapter, etc.

Other changes: the Texture that is used as input/output buffers for the compute shader are 
in RGBA8888 format, instead of GL_R8UI, so that they can be directly rendered to screen.  
The compute shader was slightly modified for this.   
The downside is the buffers now use 4 floats per pixel instead of one byte.  So it would be interesting
to find a way to make Texture with single byte "pixels".

This only runs on the desktop (LWJGL3) version.  GWT and TeaVM don't support GL31 today. 

