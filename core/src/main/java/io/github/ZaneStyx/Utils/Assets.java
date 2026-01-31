package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Centralized Asset loader using LibGDX AssetManager.
 * Reads `assets/assets.txt` and loads common textures/atlases at startup
 * to avoid runtime GPU uploads when creating SpriteAnimator instances.
 */
public final class Assets {
    public static final AssetManager manager = new AssetManager();

    private Assets() {
    }

    /**
     * Load all listed assets synchronously. Call from the GL thread during
     * application startup (e.g. ChromashiftGame.create()).
     */
    public static void loadAll() {
        Array<String> list = readAssetsList();
        for (String path : list) {
            // Skip missing files gracefully - some projects may list optional assets.
            boolean exists = Gdx.files.internal(path).exists();
            if (!exists) {
                Gdx.app.error("Assets", "Missing asset listed in assets.txt, skipping: " + path);
                continue;
            }

            // Register assets by type so AssetManager can load them properly.
            try {
                if (path.endsWith(".png") || path.endsWith(".jpg")) {
                    if (!manager.isLoaded(path, Texture.class))
                        manager.load(path, Texture.class);
                } else if (path.endsWith(".atlas")) {
                    if (!manager.isLoaded(path, TextureAtlas.class))
                        manager.load(path, TextureAtlas.class);
                } else if (path.endsWith(".fnt")) {
                    // Load bitmap fonts so their referenced textures are uploaded by AssetManager
                    if (!manager.isLoaded(path, BitmapFont.class))
                        manager.load(path, BitmapFont.class);
                } else if (path.endsWith(".skin") || (path.endsWith(".json")
                        && (path.toLowerCase().contains("uiskin") || path.toLowerCase().startsWith("ui/")))) {
                    // Only treat known UI JSON files as Skin (they reference an atlas).
                    // Avoid loading arbitrary .json files (levels, data) as Skin which would cause
                    // the Skin loader to attempt to load a similarly-named .atlas.
                    if (!manager.isLoaded(path, Skin.class))
                        manager.load(path, Skin.class);
                } else {
                    // ignore other files for now (tmx, tsx, raw data). If needed we can extend
                    // this.
                }
            } catch (Exception e) {
                Gdx.app.error("Assets", "Failed to queue asset for loading: " + path, e);
            }
        }

        // Block until finished so all textures are uploaded on the GL thread.
        try {
            manager.finishLoading();
            Gdx.app.log("Assets", "Finished loading assets: count=" + manager.getAssetNames().size);
        } catch (Exception e) {
            Gdx.app.error("Assets", "Error loading assets", e);
        }
    }

    /**
     * Queue all assets for asynchronous loading. Call this once (e.g. in a LoadingScreen)
     * and then poll {@link AssetManager#update()} each frame to progress loading.
     */
    public static void queueAll() {
        Array<String> list = readAssetsList();
        for (String path : list) {
            boolean exists = Gdx.files.internal(path).exists();
            if (!exists) {
                Gdx.app.error("Assets", "Missing asset listed in assets.txt, skipping: " + path);
                continue;
            }

            try {
                if (path.endsWith(".png") || path.endsWith(".jpg")) {
                    if (!manager.isLoaded(path, Texture.class))
                        manager.load(path, Texture.class);
                } else if (path.endsWith(".atlas")) {
                    if (!manager.isLoaded(path, TextureAtlas.class))
                        manager.load(path, TextureAtlas.class);
                } else if (path.endsWith(".fnt")) {
                    if (!manager.isLoaded(path, BitmapFont.class))
                        manager.load(path, BitmapFont.class);
                } else if (path.endsWith(".skin") || (path.endsWith(".json")
                        && (path.toLowerCase().contains("uiskin") || path.toLowerCase().startsWith("ui/")))) {
                    if (!manager.isLoaded(path, Skin.class))
                        manager.load(path, Skin.class);
                } else {
                    // ignore other file types here; they are loaded on demand elsewhere
                }
            } catch (Exception e) {
                Gdx.app.error("Assets", "Failed to queue asset for loading: " + path, e);
            }
        }
    }

    private static Array<String> readAssetsList() {
        Array<String> out = new Array<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Gdx.files.internal("assets.txt").read()))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                out.add(line);
            }
        } catch (Exception e) {
            Gdx.app.error("Assets", "Failed to read assets.txt", e);
        }
        return out;
    }

    public static void dispose() {
        try {
            manager.dispose();
        } catch (Exception ignored) {
        }
    }
}
