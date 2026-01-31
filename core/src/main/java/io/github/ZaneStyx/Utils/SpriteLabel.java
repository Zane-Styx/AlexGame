package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

/**
 * Scene2D widget that displays text using a SpriteFont.
 * Behaves like a standard Label but uses sprite-based character rendering.
 * 
 * Features:
 * - Runtime text updates
 * - Color tinting
 * - Scaling
 * - Alignment support
 * - Efficient batched rendering
 */
public class SpriteLabel extends Widget {
    private SpriteFont font;
    private String text;
    private float scale = 1f;
    private Align alignment = Align.LEFT;
    
    public enum Align {
        LEFT, CENTER, RIGHT
    }
    
    /**
     * Creates a SpriteLabel with the given font and text.
     */
    public SpriteLabel(SpriteFont font, String text) {
        this.font = font;
        this.text = text == null ? "" : text;
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        updateSize();
    }
    
    /**
     * Creates a SpriteLabel with custom scale.
     */
    public SpriteLabel(SpriteFont font, String text, float scale) {
        this.font = font;
        this.text = text == null ? "" : text;
        this.scale = scale;
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        updateSize();
    }
    
    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        
        // Apply parent alpha to font color
        Color color = getColor();
        Color fontColor = font.getColor();
        float oldA = fontColor.a;
        fontColor.a *= parentAlpha * color.a;
        font.setColor(fontColor);
        
        // Calculate position based on alignment
        float x = getX();
        float y = getY();
        float textWidth = font.getWidth(text, scale);
        
        switch (alignment) {
            case CENTER:
                x += (getWidth() - textWidth) / 2f;
                break;
            case RIGHT:
                x += getWidth() - textWidth;
                break;
            case LEFT:
            default:
                // x remains unchanged
                break;
        }
        // Draw the text
        font.draw((SpriteBatch) batch, text, x, y, scale);
        
        // Restore original alpha
        fontColor.a = oldA;
        font.setColor(fontColor);
    }
    
    /**
     * Sets the text to display.
     */
    public void setText(String text) {
        if (text == null) text = "";
        if (!this.text.equals(text)) {
            this.text = text;
            updateSize();
        }
    }
    
    /**
     * Gets the current text.
     */
    public String getText() {
        return text;
    }
    
    /**
     * Sets the font used for rendering.
     */
    public void setFont(SpriteFont font) {
        if (this.font != font) {
            this.font = font;
            updateSize();
        }
    }
    
    /**
     * Gets the current font.
     */
    public SpriteFont getFont() {
        return font;
    }
    
    /**
     * Sets the scale factor for the text.
     */
    public void setScale(float scale) {
        if (this.scale != scale) {
            this.scale = scale;
            updateSize();
        }
    }
    
    /**
     * Gets the current scale factor.
     */
    public float getScale() {
        return scale;
    }
    
    /**
     * Sets the text alignment.
     */
    public void setAlignment(Align alignment) {
        this.alignment = alignment;
    }
    
    /**
     * Gets the current text alignment.
     */
    public Align getAlignment() {
        return alignment;
    }
    
    /**
     * Sets the color tint for the text.
     */
    @Override
    public void setColor(Color color) {
        super.setColor(color);
        if (font != null) {
            font.setColor(color);
        }
    }
    
    /**
     * Sets the color tint for the text.
     */
    @Override
    public void setColor(float r, float g, float b, float a) {
        super.setColor(r, g, b, a);
        if (font != null) {
            font.setColor(r, g, b, a);
        }
    }
    
    /**
     * Sets the character spacing.
     */
    public void setSpacing(float spacing) {
        if (font != null) {
            font.setSpacing(spacing);
            updateSize();
        }
    }
    
    /**
     * Gets the character spacing.
     */
    public float getSpacing() {
        return font != null ? font.getSpacing() : 0;
    }
    
    /**
     * Updates the widget's preferred size based on text dimensions.
     */
    private void updateSize() {
        if (font != null && text != null) {
            // Widget does not provide setPrefSize; set the actual actor size to match preferred dimensions.
            setSize(font.getWidth(text, scale), font.getHeight(scale));
        }
    }
    
    @Override
    public float getPrefWidth() {
        return font != null ? font.getWidth(text, scale) : 0;
    }
    
    @Override
    public float getPrefHeight() {
        return font != null ? font.getHeight(scale) : 0;
    }
}
