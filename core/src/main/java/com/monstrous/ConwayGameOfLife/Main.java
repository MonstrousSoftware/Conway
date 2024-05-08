package com.monstrous.ConwayGameOfLife;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.badlogic.gdx.graphics.GL31.*;
import static java.lang.Integer.parseInt;

/**
 * Conway's Game of Life using OpenGL compute shader.
 *
 * LibGDX version by Monstrous Software (May 2024)
 * Requires LibGDX adaptation to provide the following GL methods:
 *         gl.glBindImageTexture();
 *         gl.glDispatchCompute();
 *         gl.glMemoryBarrier()
 *
 * Based on LWJGL3 demo by Kai Burjack
 * https://www.youtube.com/watch?v=h7aCroRpkN0
 * https://github.com/LWJGL/lwjgl3-demos/blob/main/src/org/lwjgl/demo/opengl/shader/GameOfLife.java
 */

public class Main extends InputAdapter implements ApplicationListener {
    private SpriteBatch batch;
    private ExtendViewport viewport;

    private static final boolean DEBUG = true;

    private static final int MAX_NUM_CELLS_X = 1024 * 4;
    private static final int MAX_NUM_CELLS_Y = 1024 * 4;
    private static final int WORK_GROUP_SIZE_X = 16;
    private static final int WORK_GROUP_SIZE_Y = 16;

    private int iterationProgram;
    private Texture[] textures;
    private int readTexIndex;
    private final List<GolPattern> patterns = new ArrayList<>();
    private float zoom = 1f;
    private boolean paused = false;
    private boolean step = false;
    private final Vector2 prevTouch = new Vector2();

    @Override
    public void create() {
        if (Gdx.gl31 == null) {
            throw new GdxRuntimeException("GLES 3.1 profile required for this programme.");
        }
        Gdx.app.log("LibGDX version: ", Version.VERSION);

        batch = new SpriteBatch();
        viewport = new ExtendViewport(MAX_NUM_CELLS_X, MAX_NUM_CELLS_Y);
        viewport.getCamera().position.set(MAX_NUM_CELLS_X/2, MAX_NUM_CELLS_Y/2, 0);

        iterationProgram = createIterationProgram();

        createTextures();
        loadPatterns();
        initState();

        Gdx.input.setInputProcessor( this );
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
    }

    @Override
    public void render() {
        // process keyboard input
        handleKeys();

        // call compute shader to iterate one step
        if(!paused || step)
            computeNextState();

        // render the texture to the screen
        ScreenUtils.clear(Color.BLACK, false);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.draw(textures[readTexIndex], 0, 0);
        batch.end();

        // switch input and output buffer for next iteration
        if(!paused || step)
            readTexIndex = 1 - readTexIndex;
        step = false;
    }

    private void handleKeys(){
        if(Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE))
            Gdx.app.exit();
        if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE))
            paused = !paused;
        if(Gdx.input.isKeyJustPressed(Input.Keys.S))
            step = true;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        for(Texture tex : textures )
            tex.dispose();
        for(GolPattern pat : patterns)
            pat.pixmap.dispose();
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if(amountY > 0)
            zoom *= 1.1f;
        else
            zoom *= 0.9f;

        Gdx.app.log("zoom", ""+zoom);
        ((OrthographicCamera)viewport.getCamera()).zoom = zoom;
        return true;
    }



    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        prevTouch.set(screenX, screenY);
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        float dx = screenX - prevTouch.x;
        float dy = screenY - prevTouch.y;
        prevTouch.set(screenX, screenY);
        viewport.getCamera().position.add(-dx*zoom*5f, dy*zoom*5f, 0);
        return true;
    }

    private void createTextures() {

        textures = new Texture[2];
        for (int i = 0; i < textures.length; i++) {
            Texture tex = new Texture(MAX_NUM_CELLS_X, MAX_NUM_CELLS_Y, Pixmap.Format.RGBA8888);
            tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            tex.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
            textures[i] = tex;
        }
    }

    private void initState() {
        Texture tex = textures[readTexIndex];

        // place a row of glider guns at the top
        for (int x = 0; x < MAX_NUM_CELLS_X - 40; x += 39)
            tex.draw(patterns.get(0).pixmap, x, 300);

        // fill rest with random patterns
        Random rnd = new Random();
        for (int x = 0; x < MAX_NUM_CELLS_X - 40; x += 80) {
            int incr = 0;
            for (int y = 600; y < MAX_NUM_CELLS_Y - 200; y += incr) {
                GolPattern p = choosePattern(rnd);
                tex.draw(p.pixmap, x, y);
                incr = p.height + 80;
            }
        }
    }


    private static int createShader(String resource, int type, Map<String, String> defines) {
        GL20 gl = Gdx.gl20;

        int shader = gl.glCreateShader(type);
        if (shader == 0) return -1;

        String source = Gdx.files.internal(resource).readString();

        // insert the list of #defines at the line which reads #pragma {{DEFINES}}
        source = source.replace("#pragma {{DEFINES}}",
            defines.entrySet().stream().map(e -> "#define " + e.getKey() + " " + e.getValue()).collect(Collectors.joining("\n")));

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

    private int createIterationProgram()  {
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

            String programLog = Gdx.gl.glGetProgramInfoLog(program);
            if (programLog.trim().length() > 0)
                System.err.println(programLog);
            if (linked == 0)
                throw new AssertionError("Could not link program");
        }
        iterationProgram = program;
        return program;
    }

    private void computeNextState() {
        GL31 gl = Gdx.gl31;

        gl.glUseProgram(iterationProgram);

        // new call (not in standard LibGDX)
        gl.glBindImageTexture(0, textures[readTexIndex].getTextureObjectHandle(), 0, false, 0, GL_READ_ONLY, GL_RGBA8);
        gl.glBindImageTexture(1, textures[1 - readTexIndex].getTextureObjectHandle(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA8);

        int numWorkGroupsX = MAX_NUM_CELLS_X / WORK_GROUP_SIZE_X;
        int numWorkGroupsY = MAX_NUM_CELLS_Y / WORK_GROUP_SIZE_Y;

        // new call (not in standard LibGDX)
        gl.glDispatchCompute(numWorkGroupsX, numWorkGroupsY, 1);

        // new call (not in standard LibGDX)
        gl.glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    /*
     * Patterns
     */

    private static class GolPattern {
        int height;
        Pixmap pixmap;
    }
    private GolPattern choosePattern(Random rnd) {
        return patterns.get(rnd.nextInt(patterns.size() - 1) + 1);
    }

    private void loadPatterns()  {
        // read the list of patterns
        String patternList = Gdx.files.internal("spaceships/patterns.txt").readString();
        // format per line of the list
        // example line:    Gosperglidergun.png 38 11 0/1 0/1
        //                  file-name width height <ignored> <ignored>
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
            float scaleX = pixmap.getWidth() / width;
            float scaleY = pixmap.getHeight() / height;

            Pixmap pm = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            pm.setColor(Color.BLACK);

            // add each black pixel as a point of the pattern

            // note the source image is scaled up and may have grid lines so take samples
            // and copy to an exact fit pixmap
            //
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixmap.getPixel((int)((x+0.5f)*scaleX),(int)((y+0.5f)*scaleY));
                    if(pixel == 0xFFFFFFFF) {  // black pixel
                        pm.drawPixel(x,y);
                    }
                }
            }
            pat.pixmap = pm;
            patterns.add(pat);  // add to list of patterns
        }
    }
}
