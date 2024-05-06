package com.monstrous.ConwayGameOfLife;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.badlogic.gdx.graphics.GL20.*;
import static com.badlogic.gdx.graphics.GL30.GL_R8UI;
import static com.badlogic.gdx.graphics.GL30.GL_RED;
import static com.badlogic.gdx.graphics.GL31.*;
import static com.badlogic.gdx.graphics.GL32.GL_CLAMP_TO_BORDER;
import static java.lang.Integer.parseInt;

/**
 * Conway's Game of Life using OpenGL compute shader.
 *
 * LibGDX version by Monstrous Software
 * (Requires LibGDX extensions)
 *
 * Based on LWJGL3 demo by Kai Burjack
 * https://github.com/LWJGL/lwjgl3-demos/blob/main/src/org/lwjgl/demo/opengl/shader/GameOfLife.java
 */

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture image;

    private static final boolean DEBUG = true;

    private static final int MAX_NUM_CELLS_X = 1024 * 2;
    private static final int MAX_NUM_CELLS_Y = 1024 * 2;
    private static final int WORK_GROUP_SIZE_X = 16;
    private static final int WORK_GROUP_SIZE_Y = 16;

    private static int iterationProgram;
    private static Texture textures[];
    private static int readTexIndex;
    private static List<GolPattern> patterns = new ArrayList<>();

    @Override
    public void create() {
        batch = new SpriteBatch();
        image = new Texture("libgdx.png");
        iterationProgram = createIterationProgram();
        Gdx.app.log("iterationProgram", ""+iterationProgram);

        createTextures();
        loadPatterns();

    }

    @Override
    public void render() {

        computeNextState();

//        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f);
//        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ScreenUtils.clear(Color.LIGHT_GRAY, true);

        // todo draw texture

        batch.begin();

        batch.draw(textures[readTexIndex], 0, 0);   // beware: this is only an alpha texture

        //batch.draw(image, 140, 210);
        batch.end();

        readTexIndex = 1 - readTexIndex;    // switch input and output buffer for next iteration
    }

    @Override
    public void dispose() {
        batch.dispose();
        image.dispose();
    }


//    private static void draw() {
//        GL20 gl = Gdx.gl;
//
//        gl.glUseProgram(renderProgram);
//        try (MemoryStack stack = stackPush()) {
//            float ar = (float) width / height;
//            proj.identity().view(-1.0f * ar, 1.0f * ar, -1, +1).mul(view);
//            glUniformMatrix4fv(renderProgramMatUniform, false, proj.get4x4(stack.mallocFloat(16)));
//        }
//        gl.glBindTexture(GL_TEXTURE_2D, textures[readTexIndex]);
//        gl.glBindVertexArray(vao);
//        gl.glDrawArrays(GL_TRIANGLES, 0, 3);
//    }

    private static void createTextures() {

        textures = new Texture[2];
        for (int i = 0; i < textures.length; i++) {
            Texture tex = new Texture(MAX_NUM_CELLS_X, MAX_NUM_CELLS_Y, Pixmap.Format.Alpha); // = GL_UNSIGNED_BYTE
            tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            tex.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
            textures[i] = tex;
        }
//
//        GL20 gl = Gdx.gl;
//
//        // can we use LibGDX Textures for this, or GLTexture?
//
//        textures = new int[2];
//        for (int i = 0; i < textures.length; i++) {
//            int tex = gl.glGenTexture();
//            gl.glBindTexture(GL_TEXTURE_2D, tex);
//            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
//            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
//            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
//            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
//
//            // OpenGL4.2 onwards
//            //gl.glTexStorage2D(GL_TEXTURE_2D, 1, GL_R8UI, MAX_NUM_CELLS_X, MAX_NUM_CELLS_Y);
//            //equivalent:
//            gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_R8UI, MAX_NUM_CELLS_X, MAX_NUM_CELLS_Y, 0, GL_RED, GL_UNSIGNED_INT, null);
//
//
//            textures[i] = tex;
//        }
    }

    private static void initState() {
        GL20 gl = Gdx.gl20;

        // should we do this with pixmap operations instead?

        gl.glBindTexture(GL_TEXTURE_2D, textures[readTexIndex].getTextureObjectHandle());
        ByteBuffer bb = ByteBuffer.allocate(MAX_NUM_CELLS_X * MAX_NUM_CELLS_Y);
        Random rnd = new Random();
        for (int x = 0; x < MAX_NUM_CELLS_X - 40; x += 39)
            loadPattern(x, 300, patterns.get(0), bb);
        for (int x = 0; x < MAX_NUM_CELLS_X - 40; x += 80) {
            int incr = 0;
            for (int y = 600; y < MAX_NUM_CELLS_Y - 200; y += incr) {
                GolPattern p = choosePattern(rnd);
                loadPattern(x, y, p, bb);
                incr = p.height + 80;
            }
        }
        bb.flip();
        gl.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, MAX_NUM_CELLS_X, MAX_NUM_CELLS_Y, GL_RED_INTEGER, GL_UNSIGNED_BYTE, bb);

        // todo memleak
        //memFree(bb);

        gl.glBindTexture(GL_TEXTURE_2D, 0); // unbind
    }


    private static int createShader(String resource, int type, Map<String, String> defines) {
        GL20 gl = Gdx.gl20;

        int shader = gl.glCreateShader(type);
        if (shader == 0) return -1;

        String source = Gdx.files.internal(resource).readString();
        source = source.replace("#pragma {{DEFINES}}",
            defines.entrySet().stream().map(e -> "#define " + e.getKey() + " " + e.getValue()).collect(Collectors.joining("\n")));

        Gdx.app.log("shader source:", source);

        gl.glShaderSource(shader, source);
        Gdx.gl.glCompileShader(shader);
        if (DEBUG) {
            IntBuffer intbuf = BufferUtils.newIntBuffer(1);
            gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, intbuf);
            int compiled = intbuf.get(0);
            String log = Gdx.gl.glGetShaderInfoLog(shader);
            if (log.trim().length() > 0)
                System.err.println(log);
            if (compiled == 0)
                throw new AssertionError("Could not compile shader: " + resource);
        }
        return shader;
    }

    private static int createIterationProgram()  {
        GL20 gl = Gdx.gl20;

        int program = Gdx.gl.glCreateProgram();
        Map<String, String> defines = new HashMap<>();
        defines.put("WX", WORK_GROUP_SIZE_X + "u");
        defines.put("WY", WORK_GROUP_SIZE_Y + "u");

        int cshader = createShader("shaders/iteration.cs.glsl", GL_COMPUTE_SHADER, defines);
        if(cshader == -1)
            return -1;
        gl.glAttachShader(program, cshader);
        gl.glLinkProgram(program);
        gl.glDeleteShader(cshader);
        if (DEBUG) {
            ByteBuffer tmp = ByteBuffer.allocateDirect(4);
            tmp.order(ByteOrder.nativeOrder());
            IntBuffer intbuf = tmp.asIntBuffer();

            gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, intbuf);
            int linked = intbuf.get(0);

            //int linked = Gdx.gl.glGetProgrami(program, GL_LINK_STATUS);
            String programLog = Gdx.gl.glGetProgramInfoLog(program);
            if (programLog.trim().length() > 0)
                System.err.println(programLog);
            if (linked == 0)
                throw new AssertionError("Could not link program");
        }
        iterationProgram = program;
        return program;
    }

    private static void computeNextState() {
        GL20 gl = Gdx.gl20;

//        gl.glUseProgram(iterationProgram);
//        gl.glBindImageTexture(0, textures[readTexIndex], 0, false, 0, GL_READ_ONLY, GL_R8UI);
//        gl.glBindImageTexture(1, textures[1 - readTexIndex], 0, false, 0, GL_WRITE_ONLY, GL_R8UI);
//        int numWorkGroupsX = MAX_NUM_CELLS_X / WORK_GROUP_SIZE_X;
//        int numWorkGroupsY = MAX_NUM_CELLS_Y / WORK_GROUP_SIZE_Y;
//
//        // OpenGL 4.3+
//        gl.glDispatchCompute(numWorkGroupsX, numWorkGroupsY, 1);
//
//        // OpenGL 4.2+
//        gl.glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    /*
     * Patterns
     */

    private static class GolPattern {
        List<GridPoint2> points = new ArrayList<>();
        int height;
    }
    private static GolPattern choosePattern(Random rnd) {
        return patterns.get(rnd.nextInt(patterns.size() - 1) + 1);
    }
    private static void set(int x, int y, ByteBuffer bb) {
        bb.put(x + y * MAX_NUM_CELLS_X, (byte) 1);
    }
    private static void loadPattern(int x, int y, GolPattern pattern, ByteBuffer bb) {
        for (GridPoint2 c : pattern.points)
            set(x + c.x, y + c.y, bb);
    }
    private static void loadPatterns()  {
        // read the list of patterns
        String patternList = Gdx.files.internal("spaceships/patterns.txt").readString();
        // format per line of the list
        // example line:    Gosperglidergun.png 38 11 0/1 0/1
        //                  name width height ?? ??
        Pattern p = Pattern.compile("(.+?)\\s(\\d+)\\s(\\d+)(\\s(-?\\d+)/(\\d+)\\s(\\d+)/(\\d+))?");
        // iterate over list
        for( String line : patternList.split("\\r?\\n")) {
            // parse the line
            Matcher m = p.matcher(line);
            if (!m.find())
                throw new AssertionError();
            int width = parseInt(m.group(2));
            int height = parseInt(m.group(3));
            GolPattern pat = new GolPattern();  // create new pattern
            pat.height = height;
            // read the PNG file to a pixmap
            Pixmap pixmap = new Pixmap(Gdx.files.internal("spaceships/" + m.group(1)));
            // add each black pixel as a point of the pattern
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixmap.getPixel(x,y);
                    if(pixel == 0xFFFFFFFF)     // black pixel
                        pat.points.add(new GridPoint2(x, y));
                }
            }
            patterns.add(pat);  // add to list of patterns
        }
    }

}
