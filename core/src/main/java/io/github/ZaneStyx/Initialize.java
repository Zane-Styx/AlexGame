package io.github.ZaneStyx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.ZaneStyx.Utils.CameraController;

/**
 * Lightweight initializer for common per-level systems (camera, batch, font,
 * shape renderer). Levels can call this once to avoid duplicating
 * boilerplate in each level/screen.
 *
 * Usage example:
 * Initialize.Context ctx = Initialize.createCommon(800, 480);
 */
public class Initialize {

    public static class Context {
        public OrthographicCamera camera;
        public CameraController camController;
        public SpriteBatch batch;
        public ShapeRenderer shape;
        public BitmapFont font;

        /** Dispose resources created by the context (font, batch, shape). */
        public void dispose() {
            try { if (font != null) font.dispose(); } catch (Exception ignored) {}
            try { if (batch != null) batch.dispose(); } catch (Exception ignored) {}
            try { if (shape != null) shape.dispose(); } catch (Exception ignored) {}
        }
    }

    /**
     * Create and return a preconfigured Context with camera, controller, batch,
     * shape renderer and font.
     *
     * @param width viewport width
     * @param height viewport height
     */
    public static Context createCommon(float width, float height) {
        Context c = new Context();
        c.camera = new OrthographicCamera();
        c.camera.setToOrtho(false, width, height);
        c.camController = new CameraController(c.camera);
        // Default follow settings - caller may override
        c.camController.setFollowMode(CameraController.FollowTargetMode.NONE);
        c.camController.setDeadZone(120f, 80f);
        c.camController.setSmoothSpeed(3f);
        c.camController.setLookAheadEnabled(false);
        c.camController.setDebugZoneVisible(false);

        c.batch = new SpriteBatch();
        c.shape = new ShapeRenderer();
        try {
            c.font = new BitmapFont(Gdx.files.internal("ui/default.fnt"));
            c.font.getData().setScale(0.7f);
        } catch (Exception e) {
            // Font load failure should not crash initialization; create fallback
            try { c.font = new BitmapFont(); } catch (Exception ignored) { c.font = null; }
        }

        // --- SOUND MANAGER LEVEL-SPECIFIC SOUNDS ---
        // UI sounds (UISelect, Button) are initialized early in ChromashiftGame.create()
        // Here we add the gameplay-specific sounds for this level
        try {
            // load sounds here
            
        } catch (Exception e) {
            System.err.println("[Initialize] Failed to load level sounds: " + e.getMessage());
        }

        return c;
    }
}
