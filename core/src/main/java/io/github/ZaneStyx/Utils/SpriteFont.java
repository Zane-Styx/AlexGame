package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Custom sprite-based font rendering system.
 * Renders text using character sprite sheets instead of traditional bitmap fonts.
 * 
 * Each character is a sprite from a texture atlas, allowing for:
 * - Fully custom character designs
 * - Variable character spacing
 * - Color tinting
 * - Runtime text updates
 * - Multiple font styles
 * 
 * Character Layout (128x128 texture, 8x10 per character):
 * Row 1 (y=118): a-p
 * Row 2 (y=108): q-z, 1-6
 * Row 3 (y=98):  7-0, #, comma, ., !, ?, :, *, %, (, ), +, -
 * Row 4 (y=88):  \, /, =, >, <
 */
public class SpriteFont {
    private final Texture texture;
    private final ObjectMap<Character, CharacterSprite> charMap;
    private final int charWidth;
    private final int charHeight;
    private float spacing = 1f; // Additional spacing between characters
    private final Color color = new Color(Color.WHITE);
    
    /**
     * Represents a single character sprite with its texture region and advance width.
     */
    private static class CharacterSprite {
        TextureRegion region;
        int xAdvance; // How far to move cursor after drawing this character
        
        CharacterSprite(TextureRegion region, int xAdvance) {
            this.region = region;
            this.xAdvance = xAdvance;
        }
    }
    
    /**
     * Creates a SpriteFont from a character sprite sheet.
     * 
     * @param texture The sprite sheet texture (128x128 with 8x10 characters)
     */
    public SpriteFont(Texture texture) {
        this(texture, 8, 10);
    }
    
    /**
     * Creates a SpriteFont with custom character dimensions.
     * 
     * @param texture The sprite sheet texture
     * @param charWidth Width of each character in pixels
     * @param charHeight Height of each character in pixels
     */
    public SpriteFont(Texture texture, int charWidth, int charHeight) {
        this.texture = texture;
        this.charWidth = charWidth;
        this.charHeight = charHeight;
        this.charMap = new ObjectMap<>();
        
        initializeCharacterMap();
    }
    
    /**
     * Maps characters to their sprite sheet positions.
     * Layout matches the 128x128 texture with 8x10 characters.
     */
    private void initializeCharacterMap() {
        // Row 1 (y=118-128): a-p
        String row1 = "abcdefghijklmnop";
        for (int i = 0; i < row1.length(); i++) {
            addCharacter(row1.charAt(i), i * charWidth, 118, charWidth, charHeight);
        }
        
        // Row 2 (y=108-118): q-z, 1-6
        String row2 = "qrstuvwxyz123456";
        for (int i = 0; i < row2.length(); i++) {
            addCharacter(row2.charAt(i), i * charWidth, 108, charWidth, charHeight);
        }
        
        // Row 3 (y=98-108): 7-0, symbols
        char[] row3 = {'7', '8', '9', '0', '#', ',', '.', '!', '?', ':', '*', '%', '(', ')', '+', '-'};
        for (int i = 0; i < row3.length; i++) {
            addCharacter(row3[i], i * charWidth, 98, charWidth, charHeight);
        }
        
        // Row 4 (y=88-98): special symbols
        char[] row4 = {'\\', '/', '=', '>', '<'};
        for (int i = 0; i < row4.length; i++) {
            addCharacter(row4[i], i * charWidth, 88, charWidth, charHeight);
        }
        
        // Space character (invisible but advances cursor)
        CharacterSprite spaceSprite = new CharacterSprite(null, charWidth / 2);
        charMap.put(' ', spaceSprite);
    }
    
    /**
     * Adds a character to the font map.
     */
    private void addCharacter(char c, int x, int y, int width, int height) {
        // LibGDX texture coordinates are bottom-up, but our layout is top-down
        TextureRegion region = new TextureRegion(texture, x, texture.getHeight() - y - height, width, height);
        CharacterSprite sprite = new CharacterSprite(region, width);
        charMap.put(c, sprite);
    }
    
    /**
     * Draws text at the specified position.
     * 
     * @param batch SpriteBatch to draw with
     * @param text Text to draw
     * @param x X position
     * @param y Y position
     */
    public void draw(SpriteBatch batch, String text, float x, float y) {
        draw(batch, text, x, y, 1f);
    }
    
    /**
     * Draws text at the specified position with scaling.
     * 
     * @param batch SpriteBatch to draw with
     * @param text Text to draw
     * @param x X position
     * @param y Y position
     * @param scale Scale factor (1.0 = normal size)
     */
    public void draw(SpriteBatch batch, String text, float x, float y, float scale) {
        if (text == null || text.isEmpty()) return;
        
        Color oldColor = batch.getColor().cpy();
        batch.setColor(color);
        
        float cursorX = x;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Convert uppercase to lowercase for rendering
            if (Character.isUpperCase(c)) {
                c = Character.toLowerCase(c);
            }
            CharacterSprite sprite = charMap.get(c);
            
            if (sprite != null) {
                if (sprite.region != null) {
                    batch.draw(
                        sprite.region,
                        cursorX,
                        y,
                        charWidth * scale,
                        charHeight * scale
                    );
                }
                cursorX += (sprite.xAdvance + spacing) * scale;
            } else {
                // Unknown character - skip it or render as '?'
                CharacterSprite unknown = charMap.get('?');
                if (unknown != null && unknown.region != null) {
                    batch.draw(
                        unknown.region,
                        cursorX,
                        y,
                        charWidth * scale,
                        charHeight * scale
                    );
                    cursorX += (unknown.xAdvance + spacing) * scale;
                }
            }
        }
        
        batch.setColor(oldColor);
    }
    
    /**
     * Gets the width of the text when rendered.
     * 
     * @param text Text to measure
     * @return Width in pixels at scale 1.0
     */
    public float getWidth(String text) {
        return getWidth(text, 1f);
    }
    
    /**
     * Gets the width of the text when rendered with scaling.
     * 
     * @param text Text to measure
     * @param scale Scale factor
     * @return Width in pixels at the given scale
     */
    public float getWidth(String text, float scale) {
        if (text == null || text.isEmpty()) return 0;
        
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Convert uppercase to lowercase for width calculation
            if (Character.isUpperCase(c)) {
                c = Character.toLowerCase(c);
            }
            CharacterSprite sprite = charMap.get(c);
            if (sprite != null) {
                width += (sprite.xAdvance + spacing) * scale;
            }
        }
        return width;
    }
    
    /**
     * Gets the height of the font.
     */
    public float getHeight() {
        return charHeight;
    }
    
    /**
     * Gets the height of the font with scaling.
     */
    public float getHeight(float scale) {
        return charHeight * scale;
    }
    
    /**
     * Sets the color tint for rendered text.
     */
    public void setColor(Color color) {
        this.color.set(color);
    }
    
    /**
     * Sets the color tint for rendered text.
     */
    public void setColor(float r, float g, float b, float a) {
        this.color.set(r, g, b, a);
    }
    
    /**
     * Gets the current color tint.
     */
    public Color getColor() {
        return color;
    }
    
    /**
     * Sets the spacing between characters.
     * 
     * @param spacing Additional pixels between characters (can be negative)
     */
    public void setSpacing(float spacing) {
        this.spacing = spacing;
    }
    
    /**
     * Gets the current character spacing.
     */
    public float getSpacing() {
        return spacing;
    }
    
    /**
     * Disposes of the texture resources.
     */
    public void dispose() {
        if (texture != null) {
            texture.dispose();
        }
    }
}
