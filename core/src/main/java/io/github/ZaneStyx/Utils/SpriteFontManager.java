package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Manages multiple SpriteFont instances for efficient resource management.
 * Supports loading, caching, and disposal of sprite-based fonts.
 * 
 * Usage:
 * SpriteFontManager.load("default", "ui/ctm.uiskin.png");
 * SpriteFont font = SpriteFontManager.get("default");
 * SpriteLabel label = new SpriteLabel(font, "Hello World");
 */
public class SpriteFontManager implements Disposable {
    private static SpriteFontManager instance;
    private final ObjectMap<String, SpriteFont> fonts;
    
    /**
     * Font configuration for custom character dimensions.
     */
    public static class FontConfig {
        public int charWidth = 8;
        public int charHeight = 10;
        public float spacing = 1f;
        
        public FontConfig() {}
        
        public FontConfig(int charWidth, int charHeight) {
            this.charWidth = charWidth;
            this.charHeight = charHeight;
        }
        
        public FontConfig(int charWidth, int charHeight, float spacing) {
            this.charWidth = charWidth;
            this.charHeight = charHeight;
            this.spacing = spacing;
        }
    }
    
    private SpriteFontManager() {
        fonts = new ObjectMap<>();
    }
    
    /**
     * Gets the singleton instance.
     */
    public static SpriteFontManager getInstance() {
        if (instance == null) {
            instance = new SpriteFontManager();
        }
        return instance;
    }
    
    /**
     * Loads a sprite font with default configuration (8x10 characters).
     * 
     * @param name Unique name for this font
     * @param texturePath Path to the sprite sheet texture
     * @return The loaded SpriteFont
     */
    public static SpriteFont load(String name, String texturePath) {
        return getInstance().loadFont(name, texturePath, new FontConfig());
    }
    
    /**
     * Loads a sprite font with custom configuration.
     * 
     * @param name Unique name for this font
     * @param texturePath Path to the sprite sheet texture
     * @param config Font configuration (character size, spacing)
     * @return The loaded SpriteFont
     */
    public static SpriteFont load(String name, String texturePath, FontConfig config) {
        return getInstance().loadFont(name, texturePath, config);
    }
    
    /**
     * Internal method to load and cache a font.
     */
    private SpriteFont loadFont(String name, String texturePath, FontConfig config) {
        if (fonts.containsKey(name)) {
            Gdx.app.log("SpriteFontManager", "Font '" + name + "' already loaded, returning cached instance");
            return fonts.get(name);
        }
        
        try {
            Texture texture = new Texture(Gdx.files.internal(texturePath));
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            
            SpriteFont font = new SpriteFont(texture, config.charWidth, config.charHeight);
            font.setSpacing(config.spacing);
            
            fonts.put(name, font);
            Gdx.app.log("SpriteFontManager", "Loaded font '" + name + "' from " + texturePath);
            return font;
        } catch (Exception e) {
            Gdx.app.error("SpriteFontManager", "Failed to load font '" + name + "' from " + texturePath, e);
            return null;
        }
    }
    
    /**
     * Gets a loaded sprite font by name.
     * 
     * @param name Font name
     * @return The SpriteFont, or null if not found
     */
    public static SpriteFont get(String name) {
        return getInstance().fonts.get(name);
    }
    
    /**
     * Checks if a font is loaded.
     * 
     * @param name Font name
     * @return true if the font is loaded
     */
    public static boolean isLoaded(String name) {
        return getInstance().fonts.containsKey(name);
    }
    
    /**
     * Unloads a specific font and disposes its resources.
     * 
     * @param name Font name
     */
    public static void unload(String name) {
        getInstance().unloadFont(name);
    }
    
    /**
     * Internal method to unload a font.
     */
    private void unloadFont(String name) {
        SpriteFont font = fonts.remove(name);
        if (font != null) {
            font.dispose();
            Gdx.app.log("SpriteFontManager", "Unloaded font '" + name + "'");
        }
    }
    
    /**
     * Disposes all loaded fonts.
     */
    @Override
    public void dispose() {
        for (SpriteFont font : fonts.values()) {
            if (font != null) {
                font.dispose();
            }
        }
        fonts.clear();
        Gdx.app.log("SpriteFontManager", "Disposed all fonts");
    }
    
    /**
     * Disposes all fonts and resets the manager.
     */
    public static void disposeAll() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }
}
