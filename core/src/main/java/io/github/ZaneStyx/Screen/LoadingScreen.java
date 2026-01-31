package io.github.ZaneStyx.Screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import io.github.ZaneStyx.Utils.Assets;
import io.github.ZaneStyx.Utils.PythonBackendManager;

public class LoadingScreen implements Screen {
    private final Game game;
    private final Screen nextScreen;

    private OrthographicCamera camera;
    private SpriteBatch batch;
    private ShapeRenderer shape;
    private BitmapFont font;

    private boolean queued = false;
    private float progress = 0f;
    private float lastProgress = 0f;
    private float stallTime = 0f;

    public LoadingScreen(Game game, Screen nextScreen) {
        this.game = game;
        this.nextScreen = nextScreen;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        batch = new SpriteBatch();
        shape = new ShapeRenderer();
        font = new BitmapFont();

        progress = 0f;
        lastProgress = 0f;
        stallTime = 0f;

        PythonBackendManager.startServer();
    }

    @Override
    public void render(float delta) {
        if (!queued) {
            Assets.queueAll();
            queued = true;
        }

        boolean done;
        try {
            done = Assets.manager.update();
        } catch (Exception e) {
            Gdx.app.error("LoadingScreen", "AssetManager update failed, forcing finish", e);
            done = true;
        }

        float targetProgress = Assets.manager.getProgress();
        progress = MathUtils.lerp(progress, targetProgress, 0.1f);

        if (Math.abs(targetProgress - lastProgress) < 0.0005f) {
            stallTime += delta;
        } else {
            stallTime = 0f;
            lastProgress = targetProgress;
        }

        if (!done && stallTime > 2.5f) {
            try {
                Gdx.app.log("LoadingScreen", "Loading stalled, calling finishLoading()");
                Assets.manager.finishLoading();
                done = true;
            } catch (Exception e) {
                Gdx.app.error("LoadingScreen", "finishLoading failed", e);
                done = true;
            }
        }

        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        float barWidth = Math.min(500f, w * 0.7f);
        float barHeight = 20f;
        float x = (w - barWidth) * 0.5f;
        float y = (h * 0.35f);

        shape.setProjectionMatrix(camera.combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.2f, 0.2f, 0.25f, 1f);
        shape.rect(x, y, barWidth, barHeight);
        shape.setColor(0.35f, 0.7f, 0.9f, 1f);
        shape.rect(x, y, barWidth * MathUtils.clamp(progress, 0f, 1f), barHeight);
        shape.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.0f);
        font.draw(batch, "Loading... " + Math.round(progress * 100f) + "%", x, y + 45f);
        batch.end();

        if (done) {
            // Load sprite fonts after assets are ready
            try {
                io.github.ZaneStyx.Utils.SpriteFontManager.load("default", "ui/ctm.uiskin.png");
            } catch (Exception e) {
                Gdx.app.error("LoadingScreen", "Failed to load sprite font: " + e.getMessage());
            }
            
            game.setScreen(nextScreen != null ? nextScreen : new MainMenuScreen(game));
            System.out.println("[LoadingScreen] Loading complete, switching to next screen.");
            dispose();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.setToOrtho(false, width, height);
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        try { if (batch != null) batch.dispose(); } catch (Exception ignored) {}
        try { if (shape != null) shape.dispose(); } catch (Exception ignored) {}
        try { if (font != null) font.dispose(); } catch (Exception ignored) {}
    }
}
