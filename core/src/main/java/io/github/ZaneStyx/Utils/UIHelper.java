package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * UIHelper - Utility class for creating Scene2D UI elements in LibGDX.
 * 
 * This class provides static builder methods to simplify the creation of
 * common UI components like buttons, sliders, and toggle switches.
 * 
 * All returned Actors are ready to add to a Stage or Table.
 * No allocations happen during render() - create once, reuse forever.
 * 
 * Example Usage:
 * <pre>
 * // Setup
 * Stage stage = new Stage(new ScreenViewport());
 * Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
 * Table rootTable = new Table();
 * rootTable.setFillParent(true);
 * 
 * // Create UI elements
 * TextButton btn = UIHelper.createButton("Click Me", skin, new ClickListener() {
 *     public void clicked(InputEvent event, float x, float y) {
 *         System.out.println("Button clicked!");
 *     }
 * });
 * 
 * Slider slider = UIHelper.createSlider(0, 100, 1, false, skin, new ChangeListener() {
 *     public void changed(ChangeEvent event, Actor actor) {
 *         System.out.println("Value: " + ((Slider)actor).getValue());
 *     }
 * });
 * 
 * CheckBox toggle = UIHelper.createToggle(true, skin, new ChangeListener() {
 *     public void changed(ChangeEvent event, Actor actor) {
 *         System.out.println("Toggle: " + ((CheckBox)actor).isChecked());
 *     }
 * });
 * 
 * // Layout
 * rootTable.add(btn).pad(10).row();
 * rootTable.add(slider).width(200).pad(10).row();
 * rootTable.add(toggle).pad(10).row();
 * 
 * stage.addActor(rootTable);
 * </pre>
 */
public class UIHelper {

    // ========================================================================
    // SPRITE FONT CONFIGURATION
    // ========================================================================
    
    /** Default sprite font to use for all UI text (if available) */
    public static final String DEFAULT_SPRITE_FONT = "default";
    
    /** Enable sprite fonts by default (set to false to use traditional fonts) */
    public static boolean USE_SPRITE_FONTS = true;

    // ========================================================================
    // CUSTOMIZATION CONSTANTS
    // Adjust these to change default appearance of UI elements
    // ========================================================================
    
    /** Default padding for buttons (left, right, top, bottom) */
    public static float BUTTON_PAD_X = 20f;
    public static float BUTTON_PAD_Y = 10f;
    
    /** Default minimum button width */
    public static float BUTTON_MIN_WIDTH = 100f;
    
    /** Default minimum button height */
    public static float BUTTON_MIN_HEIGHT = 40f;
    
    /** Default sprite font scale for buttons */
    public static float SPRITE_FONT_SCALE = 2.0f;
    
    /** Icon size when using sprite buttons */
    public static float ICON_SIZE = 24f;
    
    /** Spacing between icon and text in buttons */
    public static float ICON_TEXT_SPACING = 8f;
    
    /** Default slider width (horizontal) or height (vertical) */
    public static float SLIDER_SIZE = 200f;
    
    /** Toggle switch text when ON */
    public static String TOGGLE_ON_TEXT = "ON";
    
    /** Toggle switch text when OFF */
    public static String TOGGLE_OFF_TEXT = "OFF";
    
    /** Color when button is hovered */
    public static com.badlogic.gdx.graphics.Color BUTTON_HOVER_COLOR = new com.badlogic.gdx.graphics.Color(1.2f, 1.2f, 1.2f, 1f);
    
    /** Color when button is disabled */
    public static com.badlogic.gdx.graphics.Color BUTTON_DISABLED_COLOR = new com.badlogic.gdx.graphics.Color(0.5f, 0.5f, 0.5f, 0.7f);
    
    /**
     * Helper method to check if sprite fonts are available and enabled.
     */
    private static boolean spritefontsAvailable() {
        return USE_SPRITE_FONTS && SpriteFontManager.isLoaded(DEFAULT_SPRITE_FONT);
    }

    // ========================================================================
    // 1. NORMAL BUTTON (TEXT ONLY)
    // ========================================================================
    
    /**
     * Creates a simple text button with automatic padding and centered text.
     * Uses sprite font by default if available, falls back to skin font.
     * 
     * @param text The button label text
     * @param skin The UI skin to use for styling
     * @param onClick The click listener (use ClickListener with clicked() method)
     * @return A ready-to-use button (TextButton or sprite-based)
     * 
     * Customization tips:
     * - Disable sprite fonts: Set UIHelper.USE_SPRITE_FONTS = false
     * - Change button size: result.setWidth()/setHeight() or use .size() in Table
     * - Change padding: Adjust BUTTON_PAD_X and BUTTON_PAD_Y constants
     * - Change colors: Edit skin's button style or use result.setColor()
     */
    public static Actor createButton(String text, Skin skin, ClickListener onClick) {
        // Use sprite button if available and enabled
        if (spritefontsAvailable()) {
            return createSpriteButton(text, DEFAULT_SPRITE_FONT, skin, onClick);
        }
        
        // Fallback to traditional text button
        TextButton button = new TextButton(text, skin);
        
        // Apply default padding
        button.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Set minimum size
        button.setSize(BUTTON_MIN_WIDTH, BUTTON_MIN_HEIGHT);
        
        // Attach click listener with sound
        if (onClick != null) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        return button;
    }

    // ========================================================================
    // 1B. BUTTON WITH HOVER AND DISABLED STATES
    // ========================================================================
    
    /**
     * Creates a text button with hover effects and disable support.
     * Brightens on hover, dims when disabled.
     * 
     * @param text The button label text
     * @param skin The UI skin to use for styling
     * @param onClick The click listener (use ClickListener with clicked() method)
     * @return A ready-to-use TextButton with hover/disabled support
     * 
     * Customization tips:
     * - Change hover color: Adjust BUTTON_HOVER_COLOR constant
     * - Change disabled color: Adjust BUTTON_DISABLED_COLOR constant
     * - Disable button: Call setButtonDisabled(button, true)
     * - Enable button: Call setButtonDisabled(button, false)
     * - Check if disabled: button.isDisabled()
     */
    public static TextButton createHoverButton(String text, Skin skin, ClickListener onClick) {
        TextButton button = new TextButton(text, skin);
        
        // Apply default padding and size
        button.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        button.setSize(BUTTON_MIN_WIDTH, BUTTON_MIN_HEIGHT);
        
        // Store original color
        final com.badlogic.gdx.graphics.Color originalColor = button.getColor().cpy();
        
        // Add hover and click listeners
        button.addListener(new ClickListener() {
            @Override
            public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                if (!button.isDisabled()) {
                    button.setColor(BUTTON_HOVER_COLOR);
                }
            }
            
            @Override
            public void exit(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                if (!button.isDisabled()) {
                    button.setColor(originalColor);
                }
            }
            
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (!button.isDisabled()) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    if (onClick != null) {
                        onClick.clicked(event, x, y);
                    }
                }
            }
        });
        
        return button;
    }

    // ========================================================================
    // 1C. DISABLE/ENABLE BUTTON UTILITY
    // ========================================================================
    
    /**
     * Set a button's disabled state with visual feedback.
     * When disabled: button dims and becomes unclickable.
     * When enabled: button returns to normal appearance and is clickable.
     * 
     * @param button The TextButton to modify
     * @param disabled True to disable, false to enable
     * 
     * Usage:
     * <pre>
     * TextButton btn = createHoverButton("Submit", skin, onClick);
     * setButtonDisabled(btn, true);  // Disable
     * setButtonDisabled(btn, false); // Enable
     * </pre>
     */
    public static void setButtonDisabled(TextButton button, boolean disabled) {
        if (button == null) return;
        
        button.setDisabled(disabled);
        
        if (disabled) {
            button.setColor(BUTTON_DISABLED_COLOR);
        } else {
            button.setColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * Set a button's disabled state with visual feedback (Container<Table> variant).
     * Use with buttons created by createButton(text, icon, skin, onClick).
     * 
     * @param buttonContainer The Container<Table> button to modify
     * @param disabled True to disable, false to enable
     */
    public static void setButtonDisabled(Container<Table> buttonContainer, boolean disabled) {
        if (buttonContainer == null || buttonContainer.getActor() == null) return;
        
        Table table = buttonContainer.getActor();
        
        if (disabled) {
            table.setColor(BUTTON_DISABLED_COLOR);
        } else {
            table.setColor(1f, 1f, 1f, 1f);
        }
    }

    // ========================================================================
    // 3. BUTTON WITH SPRITE/ICON
    // ========================================================================
    
    /**
     * Creates a button with an icon/sprite placed to the left of the text.
     * Returns a Container<Table> that acts as a button with image + text.
     * 
     * @param text The button label text
     * @param icon The texture region for the icon
     * @param skin The UI skin to use for styling
     * @param onClick The click listener
     * @return A Container wrapping a button with icon and text
     * 
     * Customization tips:
     * - Change icon size: Adjust ICON_SIZE constant
     * - Change icon position: Modify the table.add() order (icon, then label)
     * - Change spacing: Adjust ICON_TEXT_SPACING constant
     * - Use right-aligned icon: Add label first, then icon
     * - Use custom button style: Pass skin.get("styleName", TextButton.TextButtonStyle.class)
     */
    public static Container<Table> createButton(String text, TextureRegion icon, Skin skin, ClickListener onClick) {
        // Create a table to hold icon + text
        Table buttonTable = new Table(skin);
        buttonTable.setBackground(skin.getDrawable("button"));
        
        // Add icon
        if (icon != null) {
            Image iconImage = new Image(new TextureRegionDrawable(icon));
            iconImage.setSize(ICON_SIZE, ICON_SIZE);
            buttonTable.add(iconImage).size(ICON_SIZE).padRight(ICON_TEXT_SPACING);
        }
        
        // Add text label using sprite font if available
        Actor label;
        if (spritefontsAvailable()) {
            label = new SpriteLabel(SpriteFontManager.get(DEFAULT_SPRITE_FONT), text);
        } else {
            label = new Label(text, skin);
        }
        buttonTable.add(label);
        
        // Apply padding
        buttonTable.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Attach click listener with sound
        if (onClick != null) {
            buttonTable.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        // Wrap in container for consistent API
        Container<Table> container = new Container<>(buttonTable);
        container.setSize(BUTTON_MIN_WIDTH, BUTTON_MIN_HEIGHT);
        
        return container;
    }

    // ========================================================================
    // 2. IMAGE-ONLY BUTTON (PNG / ICON)
    // ========================================================================
    /**
     * Creates an image-only button using a TextureRegion (e.g. from a PNG).
     * Returns a Container wrapping a Table so it matches the other button APIs.
     * The button will play the UISelect sound when clicked and call the provided listener.
     *
     * @param icon The texture region to use as the button image (can be null)
     * @param skin The UI skin used to provide the button background drawable
     * @param onClick Click listener invoked when the button is clicked
     * @return A Container<Table> acting as an image button
     */
    public static Container<Table> createImageButton(TextureRegion icon, Skin skin, ClickListener onClick) {
        Table buttonTable = new Table(skin);
        // Use the same button background as other buttons
        try {
            buttonTable.setBackground(skin.getDrawable("button"));
        } catch (Exception e) {
            // If skin has no "button" drawable, ignore and keep transparent background
        }

        if (icon != null) {
            Image iconImage = new Image(new TextureRegionDrawable(icon));
            // Give the icon a reasonable default size
            iconImage.setSize(ICON_SIZE * 2f, ICON_SIZE * 2f);
            buttonTable.add(iconImage).size(ICON_SIZE * 2f, ICON_SIZE * 2f);
        }

        buttonTable.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);

        if (onClick != null) {
            buttonTable.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }

        Container<Table> container = new Container<>(buttonTable);
        container.setSize(BUTTON_MIN_WIDTH, BUTTON_MIN_HEIGHT);
        return container;
    }

    /**
     * Convenience overload: create an image-only button from a PNG file path.
     * The path should be relative to the assets root (e.g. "ui/mybtn.png").
     * Note: this will create a new Texture; if you intend to reuse the texture elsewhere,
     * consider creating a TextureRegion once and calling the other overload.
     */
    public static Container<Table> createImageButton(String pngPath, Skin skin, ClickListener onClick) {
        if (pngPath == null) return createImageButton((TextureRegion) null, skin, onClick);
        com.badlogic.gdx.graphics.Texture tex = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal(pngPath));
        TextureRegion region = new TextureRegion(tex);
        return createImageButton(region, skin, onClick);
    }

    // ========================================================================
    // 4. SLIDER
    // ========================================================================
    
    /**
     * Creates a slider with automatic sizing and styling.
     * 
     * @param min Minimum value
     * @param max Maximum value
     * @param step Step size (e.g., 1 for integers, 0.1 for decimals)
     * @param vertical True for vertical slider, false for horizontal
     * @param skin The UI skin to use for styling
     * @param onChange Change listener triggered when value changes
     * @return A ready-to-use Slider
     * 
     * Customization tips:
     * - Change size: Use .width()/height() in Table layout or adjust SLIDER_SIZE
     * - Change visual style: Modify skin's slider style (handle, background drawables)
     * - Get current value: slider.getValue()
     * - Set value programmatically: slider.setValue(float)
     * - Disable: slider.setDisabled(true)
     * - Add value label: Create Label next to slider, update in onChange listener
     */
    public static Slider createSlider(float min, float max, float step, boolean vertical, Skin skin, ChangeListener onChange) {
        Slider slider = new Slider(min, max, step, vertical, skin);
        
        // Set default size
        if (vertical) {
            slider.setHeight(SLIDER_SIZE);
        } else {
            slider.setWidth(SLIDER_SIZE);
        }
        
        // Attach change listener with sound
        if (onChange != null) {
            slider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onChange.changed(event, actor);
                }
            });
        }
        
        return slider;
    }

    // ========================================================================
    // 5. TOGGLE SWITCH (TEXT: ON/OFF)
    // ========================================================================
    
    /**
     * Creates a toggle switch using CheckBox styled to show "ON" or "OFF" text.
     * 
     * @param initialState True for ON, false for OFF
     * @param skin The UI skin to use for styling
     * @param onToggle Change listener triggered when toggle state changes
     * @return A CheckBox styled as a toggle switch
     * 
     * Customization tips:
     * - Change ON/OFF text: Adjust TOGGLE_ON_TEXT and TOGGLE_OFF_TEXT constants
     * - Change visual style: Modify skin's checkbox style (on/off drawables)
     * - Get state: checkbox.isChecked()
     * - Set state programmatically: checkbox.setChecked(boolean)
     * - Disable: checkbox.setDisabled(true)
     */
    public static CheckBox createToggle(boolean initialState, Skin skin, ChangeListener onToggle) {
        // Create checkbox with initial text based on state
        String initialText = initialState ? TOGGLE_ON_TEXT : TOGGLE_OFF_TEXT;
        CheckBox toggle = new CheckBox(initialText, skin);
        toggle.setChecked(initialState);
        
        // Add listener to update text when state changes with sound
        toggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                CheckBox cb = (CheckBox) actor;
                cb.setText(cb.isChecked() ? TOGGLE_ON_TEXT : TOGGLE_OFF_TEXT);
                io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                
                // Call user's listener
                if (onToggle != null) {
                    onToggle.changed(event, actor);
                }
            }
        });
        
        return toggle;
    }

    // ========================================================================
    // 6. TOGGLE SWITCH (WITH SPRITE/ICON)
    // ========================================================================
    
    /**
     * Creates a toggle switch that displays different sprites for ON and OFF states.
     * Returns a Container<Table> with an Image that swaps between sprites.
     * 
     * @param initialState True for ON, false for OFF
     * @param onSprite Texture region shown when toggle is ON
     * @param offSprite Texture region shown when toggle is OFF
     * @param skin The UI skin to use for styling
     * @param onToggle Change listener triggered when toggle state changes
     * @return A Container wrapping a clickable image toggle
     * 
     * Customization tips:
     * - Change icon size: Adjust ICON_SIZE constant
     * - Add border/background: Use table.setBackground(skin.getDrawable("name"))
     * - Add text beside icon: Add Label to the table
     * - Animate transition: Use Actions in the change listener
     * - Get state: Store in user data with actor.setUserObject(Boolean)
     */
    public static Container<Table> createToggle(boolean initialState, TextureRegion onSprite, TextureRegion offSprite, Skin skin, ChangeListener onToggle) {
        // Create table to hold the toggle image
        Table toggleTable = new Table(skin);
        
        // Create image with initial sprite
        TextureRegion initialSprite = initialState ? onSprite : offSprite;
        Image toggleImage = new Image(new TextureRegionDrawable(initialSprite));
        toggleImage.setSize(ICON_SIZE, ICON_SIZE);
        
        // Store state in user object
        toggleTable.setUserObject(initialState);
        
        toggleTable.add(toggleImage).size(ICON_SIZE);
        toggleTable.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Add click listener to toggle state and swap sprite
        toggleTable.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                // Toggle state
                boolean currentState = (Boolean) toggleTable.getUserObject();
                boolean newState = !currentState;
                toggleTable.setUserObject(newState);
                
                // Swap sprite
                TextureRegion newSprite = newState ? onSprite : offSprite;
                toggleImage.setDrawable(new TextureRegionDrawable(newSprite));
                
                // Play UI sound
                io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                
                // Call user's change listener
                if (onToggle != null) {
                    ChangeListener.ChangeEvent changeEvent = new ChangeListener.ChangeEvent();
                    changeEvent.setTarget(toggleTable);
                    onToggle.changed(changeEvent, toggleTable);
                }
            }
        });
        
        // Wrap in container
        Container<Table> container = new Container<>(toggleTable);
        
        return container;
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    /**
     * Helper to check toggle sprite state.
     * Use with createToggle(sprites) result.
     * 
     * @param toggleContainer The container returned by createToggle with sprites
     * @return True if ON, false if OFF
     */
    public static boolean getToggleState(Container<Table> toggleContainer) {
        if (toggleContainer == null || toggleContainer.getActor() == null) {
            return false;
        }
        Object state = toggleContainer.getActor().getUserObject();
        return state != null && (Boolean) state;
    }
    
    /**
     * Helper to set toggle sprite state programmatically.
     * 
     * @param toggleContainer The container returned by createToggle with sprites
     * @param state True for ON, false for OFF
     * @param onSprite Sprite to show when ON
     * @param offSprite Sprite to show when OFF
     */
    public static void setToggleState(Container<Table> toggleContainer, boolean state, TextureRegion onSprite, TextureRegion offSprite) {
        if (toggleContainer == null || toggleContainer.getActor() == null) {
            return;
        }
        
        Table table = toggleContainer.getActor();
        table.setUserObject(state);
        
        // Find and update image
        if (table.getChildren().size > 0 && table.getChildren().first() instanceof Image) {
            Image img = (Image) table.getChildren().first();
            TextureRegion sprite = state ? onSprite : offSprite;
            img.setDrawable(new TextureRegionDrawable(sprite));
        }
    }

    // ========================================================================
    // EXAMPLE USAGE DEMO
    // ========================================================================
    
    /**
     * Example demonstrating how to use UIHelper to build a complete UI.
     * Call this from a Screen's show() method.
     * 
     * @param stage The Stage to add UI to
     * @param skin The Skin containing UI styles
     * 
     * Example in a Screen class:
     * <pre>
     * public void show() {
     *     stage = new Stage(new ScreenViewport());
     *     skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
     *     Gdx.input.setInputProcessor(stage);
     *     UIHelper.buildExampleUI(stage, skin);
     * }
     * 
     * public void render(float delta) {
     *     ScreenUtils.clear(0.2f, 0.2f, 0.2f, 1f);
     *     stage.act(delta);
     *     stage.draw();
     * }
     * </pre>
     */
    public static void buildExampleUI(com.badlogic.gdx.scenes.scene2d.Stage stage, Skin skin) {
        // Create root table
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.defaults().pad(10);
        
        // Title
        Label title = new Label("UIHelper Demo", skin);
        title.setFontScale(1.5f);
        rootTable.add(title).colspan(2).padBottom(20).row();
        
        // 1. Simple text button
        Actor simpleBtn = createButton("Simple Button", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                System.out.println("Simple button clicked!");
            }
        });
        rootTable.add(new Label("Text Button:", skin)).right();
        rootTable.add(simpleBtn).left().row();
        
        // 2. Button with icon (if you have an icon texture)
        // TextureRegion icon = new TextureRegion(new Texture("icon.png"));
        // Container<Table> iconBtn = createButton("Icon Button", icon, skin, new ClickListener() {
        //     @Override
        //     public void clicked(InputEvent event, float x, float y) {
        //         System.out.println("Icon button clicked!");
        //     }
        // });
        // rootTable.add(new Label("Icon Button:", skin)).right();
        // rootTable.add(iconBtn).left().row();
        
        // 3. Horizontal slider
        final Label sliderValue = new Label("50", skin);
        Slider slider = createSlider(0, 100, 1, false, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float val = ((Slider) actor).getValue();
                sliderValue.setText(String.format("%.0f", val));
                System.out.println("Slider value: " + val);
            }
        });
        slider.setValue(50);
        
        rootTable.add(new Label("Slider:", skin)).right();
        Table sliderRow = new Table();
        sliderRow.add(slider).width(200);
        sliderRow.add(sliderValue).padLeft(10);
        rootTable.add(sliderRow).left().row();
        
        // 4. Text toggle
        CheckBox toggle = createToggle(true, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean checked = ((CheckBox) actor).isChecked();
                System.out.println("Toggle state: " + (checked ? "ON" : "OFF"));
            }
        });
        rootTable.add(new Label("Toggle:", skin)).right();
        rootTable.add(toggle).left().row();
        
        // 5. Sprite toggle (if you have on/off sprites)
        // TextureRegion onSprite = new TextureRegion(new Texture("on.png"));
        // TextureRegion offSprite = new TextureRegion(new Texture("off.png"));
        // Container<Table> spriteToggle = createToggle(false, onSprite, offSprite, skin, new ChangeListener() {
        //     @Override
        //     public void changed(ChangeEvent event, Actor actor) {
        //         boolean state = getToggleState((Container<Table>) actor.getParent());
        //         System.out.println("Sprite toggle: " + (state ? "ON" : "OFF"));
        //     }
        // });
        // rootTable.add(new Label("Sprite Toggle:", skin)).right();
        // rootTable.add(spriteToggle).left().row();
        
        // Add root table to stage
        stage.addActor(rootTable);
    }

    // ========================================================================
    // 7. IMAGE ELEMENT (SIMPLE IMAGE DISPLAY)
    // ========================================================================
    
    /**
     * Creates a simple image display element.
     * 
     * @param texture The texture region to display
     * @param width The width of the image
     * @param height The height of the image
     * @return An Image actor ready to add to a Stage or Table
     * 
     * Customization tips:
     * - Scale: Use image.scaleBy(float) or setSize(width, height)
     * - Rotation: image.setRotation(degrees)
     * - Color tint: image.setColor(Color.RED) or image.setColor(r, g, b, a)
     * - Alignment: Use Table alignment when adding to table
     */
    public static Image createImage(TextureRegion texture, float width, float height) {
        if (texture == null) {
            return new Image();
        }
        
        Image image = new Image(new TextureRegionDrawable(texture));
        image.setSize(width, height);
        
        return image;
    }

    /**
     * Creates a clickable image button (no text, just image).
     * 
     * @param texture The texture region for the button
     * @param width The width of the button
     * @param height The height of the button
     * @param onClick The click listener
     * @return A Container wrapping the clickable image
     * 
     * Customization tips:
     * - Add hover effect: Use createImageHoverButton() instead
     * - Add text overlay: Add a Label to the Table
     * - Scale on click: Use Actions in onClick listener
     */
    public static Container<Table> createImageButton(TextureRegion texture, float width, float height, ClickListener onClick) {
        Table buttonTable = new Table();
        
        Image image = new Image(new TextureRegionDrawable(texture));
        image.setSize(width, height);
        
        buttonTable.add(image).size(width, height);
        
        if (onClick != null) {
            buttonTable.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        Container<Table> container = new Container<>(buttonTable);
        container.setSize(width, height);
        
        return container;
    }

    /**
     * Creates a clickable image button with hover effect.
     * Image brightens on hover, dims when disabled.
     * 
     * @param texture The texture region for the button
     * @param width The width of the button
     * @param height The height of the button
     * @param onClick The click listener
     * @return A Container wrapping the image button with hover support
     * 
     * Customization tips:
     * - Change hover color: Adjust BUTTON_HOVER_COLOR
     * - Change disabled color: Adjust BUTTON_DISABLED_COLOR
     * - Disable button: Call setImageButtonDisabled(container, true)
     */
    public static Container<Table> createImageHoverButton(TextureRegion texture, float width, float height, ClickListener onClick) {
        Table buttonTable = new Table();
        
        Image image = new Image(new TextureRegionDrawable(texture));
        image.setSize(width, height);
        buttonTable.add(image).size(width, height);
        
        final com.badlogic.gdx.graphics.Color originalColor = image.getColor().cpy();
        
        buttonTable.addListener(new ClickListener() {
            @Override
            public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                image.setColor(BUTTON_HOVER_COLOR);
            }
            
            @Override
            public void exit(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                image.setColor(originalColor);
            }
            
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                if (onClick != null) {
                    onClick.clicked(event, x, y);
                }
            }
        });
        
        Container<Table> container = new Container<>(buttonTable);
        container.setSize(width, height);
        
        return container;
    }

    /**
     * Disable or enable an image button created with createImageButton or createImageHoverButton.
     * 
     * @param imageButtonContainer The container returned by createImageButton/createImageHoverButton
     * @param disabled True to disable, false to enable
     */
    public static void setImageButtonDisabled(Container<Table> imageButtonContainer, boolean disabled) {
        if (imageButtonContainer == null || imageButtonContainer.getActor() == null) return;
        
        Table table = imageButtonContainer.getActor();
        
        if (disabled) {
            table.setColor(BUTTON_DISABLED_COLOR);
        } else {
            table.setColor(1f, 1f, 1f, 1f);
        }
    }

    // ========================================================================
    // 8. IMAGE LABEL (IMAGE WITH TEXT OVERLAY)
    // ========================================================================
    
    /**
     * Creates an image with text overlay.
     * Text is centered on the image.
     * 
     * @param texture The background texture region
     * @param text The text to display over the image
     * @param width The width of the element
     * @param height The height of the element
     * @param skin The UI skin for text styling
     * @return A Container wrapping the image + text
     * 
     * Customization tips:
     * - Change text alignment: Use table.align() and cell.center()/left()/right()
     * - Change text color: Modify skin's Label style or use label.setColor()
     * - Add text shadow: Use custom font with shadow effect
     */
    public static Container<Table> createImageLabel(TextureRegion texture, String text, float width, float height, Skin skin) {
        Table imageTable = new Table(skin);
        
        Image background = new Image(new TextureRegionDrawable(texture));
        background.setSize(width, height);
        
        Actor label;
        if (spritefontsAvailable()) {
            label = new SpriteLabel(SpriteFontManager.get(DEFAULT_SPRITE_FONT), text);
        } else {
            label = new Label(text, skin);
        }
        
        // Stack image and label
        com.badlogic.gdx.scenes.scene2d.ui.Stack stack = new com.badlogic.gdx.scenes.scene2d.ui.Stack();
        stack.add(background);
        stack.add(label);
        
        imageTable.add(stack).size(width, height);
        
        Container<Table> container = new Container<>(imageTable);
        container.setSize(width, height);
        
        return container;
    }

    // ========================================================================
    // 9. PROGRESS BAR WITH IMAGE BACKGROUND
    // ========================================================================
    
    /**
     * Creates a progress bar with an image as the background.
     * Shows progress as a filled rectangle overlay.
     * 
     * @param backgroundTexture The background texture
     * @param fillColor The color of the progress fill
     * @param width The width of the progress bar
     * @param height The height of the progress bar
     * @param skin The UI skin
     * @return A ProgressBar styled with image background
     * 
     * Customization tips:
     * - Change fill color: Use progressBar.setColor()
     * - Set progress: progressBar.setValue(0-100)
     * - Get progress: progressBar.getValue()
     */
    public static com.badlogic.gdx.scenes.scene2d.ui.ProgressBar createImageProgressBar(TextureRegion backgroundTexture, com.badlogic.gdx.graphics.Color fillColor, float width, float height, Skin skin) {
        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle style = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle();
        style.background = new TextureRegionDrawable(backgroundTexture);
        style.knobBefore = new TextureRegionDrawable(backgroundTexture);
        
        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar progressBar = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar(0, 100, 1, false, style);
        progressBar.setSize(width, height);
        progressBar.setColor(fillColor);
        
        return progressBar;
    }

    // ========================================================================
    // 10. ICON + LABEL COMBINATION
    // ========================================================================
    
    /**
     * Creates an icon with label text (icon on left, text on right).
     * Returns a Container wrapping the layout.
     * 
     * @param icon The texture region for the icon
     * @param text The label text
     * @param iconSize The size of the icon
     * @param skin The UI skin
     * @return A Container with icon + text layout
     * 
     * Customization tips:
     * - Change spacing: Adjust cell.padRight() in method or use ICON_TEXT_SPACING
     * - Swap position: Add label first, then icon
     * - Add color: Use icon.setColor() or label.setColor()
     */
    public static Container<Table> createIconLabel(TextureRegion icon, String text, float iconSize, Skin skin) {
        Table iconTable = new Table();
        
        Image iconImage = new Image(new TextureRegionDrawable(icon));
        iconImage.setSize(iconSize, iconSize);
        
        Actor label;
        if (spritefontsAvailable()) {
            label = new SpriteLabel(SpriteFontManager.get(DEFAULT_SPRITE_FONT), text);
        } else {
            label = new Label(text, skin);
        }
        
        iconTable.add(iconImage).size(iconSize).padRight(ICON_TEXT_SPACING);
        iconTable.add(label);
        
        Container<Table> container = new Container<>(iconTable);
        
        return container;
    }

    /**
     * Creates an icon with label and description text.
     * Layout: icon | [title text] / [description text]
     * 
     * @param icon The texture region for the icon
     * @param title The main label text
     * @param description The secondary description text
     * @param iconSize The size of the icon
     * @param skin The UI skin
     * @return A Container with icon + title + description layout
     */
    public static Container<Table> createIconLabelWithDescription(TextureRegion icon, String title, String description, float iconSize, Skin skin) {
        Table iconTable = new Table();
        
        Image iconImage = new Image(new TextureRegionDrawable(icon));
        iconImage.setSize(iconSize, iconSize);
        
        Table textTable = new Table();
        Label titleLabel = new Label(title, skin);
        Label descLabel = new Label(description, skin);
        descLabel.setFontScale(0.8f);
        
        textTable.add(titleLabel).left().row();
        textTable.add(descLabel).left();
        
        iconTable.add(iconImage).size(iconSize).padRight(ICON_TEXT_SPACING);
        iconTable.add(textTable).left();
        
        Container<Table> container = new Container<>(iconTable);
        
        return container;
    }

    // ========================================================================
    // 11. IMAGE GRID / IMAGE SELECTOR
    // ========================================================================
    
    /**
     * Creates a grid of clickable images (image selector).
     * Images are arranged in rows and columns.
     * 
     * @param textures Array of texture regions to display
     * @param columns Number of columns in the grid
     * @param cellSize Size of each image cell (width and height)
     * @param onImageClick Listener triggered when an image is clicked (passes index)
     * @param skin The UI skin
     * @return A ScrollPane containing the image grid
     * 
     * Customization tips:
     * - Change grid spacing: Modify cell.pad() in method
     * - Change cell size: Adjust cellSize parameter
     * - Add selection highlight: Use getImageGridSelection() and highlight selected index
     * - Hover effects: Images will show in the cell with basic sizing
     */
    public static com.badlogic.gdx.scenes.scene2d.ui.ScrollPane createImageGrid(TextureRegion[] textures, int columns, float cellSize, final io.github.ZaneStyx.Utils.UIHelper.ImageGridListener onImageClick, Skin skin) {
        Table gridTable = new Table();
        
        for (int i = 0; i < textures.length; i++) {
            final int index = i;
            TextureRegion texture = textures[i];
            
            Image cellImage = new Image(new TextureRegionDrawable(texture));
            cellImage.setSize(cellSize, cellSize);
            
            com.badlogic.gdx.scenes.scene2d.ui.Container<Image> cellContainer = new com.badlogic.gdx.scenes.scene2d.ui.Container<>(cellImage);
            cellContainer.setSize(cellSize, cellSize);
            
            cellContainer.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    if (onImageClick != null) {
                        onImageClick.onImageSelected(index, texture);
                    }
                }
            });
            
            gridTable.add(cellContainer).size(cellSize).pad(5);
            
            if ((i + 1) % columns == 0) {
                gridTable.row();
            }
        }
        
        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scrollPane = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(gridTable, skin);
        
        return scrollPane;
    }

    /**
     * Listener interface for image grid selection.
     */
    public interface ImageGridListener {
        /**
         * Called when an image in the grid is selected.
         * 
         * @param index The index of the selected image
         * @param texture The texture region of the selected image
         */
        void onImageSelected(int index, TextureRegion texture);
    }

    // ========================================================================
    // 12. IMAGE SLIDER (VISUAL SLIDER WITH BACKGROUND IMAGE)
    // ========================================================================
    
    /**
     * Creates a slider with an image as the background/track.
     * 
     * @param min Minimum value
     * @param max Maximum value
     * @param step Step size
     * @param backgroundTexture The texture for the slider background/track
     * @param handleTexture Optional texture for the slider handle (null = default)
     * @param vertical True for vertical slider, false for horizontal
     * @param skin The UI skin
     * @param onChange Change listener
     * @return A Slider with image-based styling
     * 
     * Customization tips:
     * - Change handle appearance: Provide handleTexture or modify skin
     * - Change track color: Use slider.setColor()
     * - Adjust size: Use container.setSize() or Table sizing
     */
    public static com.badlogic.gdx.scenes.scene2d.ui.Slider createImageSlider(float min, float max, float step, TextureRegion backgroundTexture, TextureRegion handleTexture, boolean vertical, Skin skin, ChangeListener onChange) {
        com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle style = new com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle();
        style.background = new TextureRegionDrawable(backgroundTexture);
        
        if (handleTexture != null) {
            style.knob = new TextureRegionDrawable(handleTexture);
        } else {
            // Use skin's default knob if not provided
            style.knob = skin.getDrawable("default-slider-knob");
        }
        
        com.badlogic.gdx.scenes.scene2d.ui.Slider slider = new com.badlogic.gdx.scenes.scene2d.ui.Slider(min, max, step, vertical, style);
        
        if (vertical) {
            slider.setHeight(SLIDER_SIZE);
        } else {
            slider.setWidth(SLIDER_SIZE);
        }
        
        if (onChange != null) {
            slider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onChange.changed(event, actor);
                }
            });
        }
        
        return slider;
    }

    // ========================================================================
    // 13. IMAGE DIVIDER (VISUAL SEPARATOR)
    // ========================================================================
    
    /**
     * Creates a visual divider/separator using an image.
     * Useful for separating sections in UI layouts.
     * 
     * @param texture The texture region for the divider
     * @param width The width of the divider
     * @param height The height of the divider (usually small)
     * @return An Image actor that can be added to layouts
     * 
     * Customization tips:
     * - Horizontal divider: Use width=large, height=small
     * - Vertical divider: Use width=small, height=large
     * - Stretch to fill: Use Table.fill() on the cell
     */
    public static Image createImageDivider(TextureRegion texture, float width, float height) {
        Image divider = new Image(new TextureRegionDrawable(texture));
        divider.setSize(width, height);
        
        return divider;
    }

    // ========================================================================
    // 14. UTILITY: CHANGE IMAGE TEXTURE AT RUNTIME
    // ========================================================================
    
    /**
     * Changes the texture of an image element at runtime.
     * Useful for dynamic UI updates.
     * 
     * @param image The Image actor to update
     * @param newTexture The new texture region to display
     */
    public static void setImageTexture(Image image, TextureRegion newTexture) {
        if (image == null || newTexture == null) return;
        
        image.setDrawable(new TextureRegionDrawable(newTexture));
    }

    /**
     * Changes the texture of an image button at runtime.
     * 
     * @param imageButtonContainer The container from createImageButton/createImageHoverButton
     * @param newTexture The new texture region to display
     */
    public static void setImageButtonTexture(Container<Table> imageButtonContainer, TextureRegion newTexture) {
        if (imageButtonContainer == null || imageButtonContainer.getActor() == null) return;
        
        Table table = imageButtonContainer.getActor();
        if (table.getChildren().size > 0 && table.getChildren().first() instanceof Image) {
            Image img = (Image) table.getChildren().first();
            setImageTexture(img, newTexture);
        }
    }

    /**
     * Gets the image from an image button container.
     * 
     * @param imageButtonContainer The container from createImageButton/createImageHoverButton
     * @return The Image actor, or null if not found
     */
    public static Image getImageFromButton(Container<Table> imageButtonContainer) {
        if (imageButtonContainer == null || imageButtonContainer.getActor() == null) return null;
        
        Table table = imageButtonContainer.getActor();
        if (table.getChildren().size > 0 && table.getChildren().first() instanceof Image) {
            return (Image) table.getChildren().first();
        }
        
        return null;
    }

    // ========================================================================
    // 15. SPRITE-BASED LABELS (NO .FNT FILES)
    // ========================================================================
    
    /**
     * Creates a sprite-based label using a registered font.
     * This uses image-based character rendering instead of .fnt files.
     * 
     * @param text The text to display
     * @param fontName The name of the loaded sprite font (use SpriteFontManager.load first)
     * @return A SpriteLabel widget ready to add to the stage
     * 
     * Example:
     * SpriteFontManager.load("default", "ui/ctm.uiskin.png");
     * SpriteLabel label = UIHelper.createSpriteLabel("HELLO WORLD", "default");
     * table.add(label).pad(10).row();
     */
    public static SpriteLabel createSpriteLabel(String text, String fontName) {
        SpriteFont font = SpriteFontManager.get(fontName);
        if (font == null) {
            Gdx.app.error("UIHelper", "Font '" + fontName + "' not loaded. Use SpriteFontManager.load() first.");
            return null;
        }
        return new SpriteLabel(font, text);
    }
    
    /**
     * Creates a sprite-based label with custom scale.
     * 
     * @param text The text to display
     * @param fontName The name of the loaded sprite font
     * @param scale Scale factor (1.0 = normal size, 2.0 = double size)
     * @return A SpriteLabel widget
     */
    public static SpriteLabel createSpriteLabel(String text, String fontName, float scale) {
        SpriteFont font = SpriteFontManager.get(fontName);
        if (font == null) {
            Gdx.app.error("UIHelper", "Font '" + fontName + "' not loaded. Use SpriteFontManager.load() first.");
            return null;
        }
        return new SpriteLabel(font, text, scale);
    }
    
    /**
     * Creates a sprite-based label with custom scale and color.
     * 
     * @param text The text to display
     * @param fontName The name of the loaded sprite font
     * @param scale Scale factor
     * @param color Color tint for the text
     * @return A SpriteLabel widget
     */
    public static SpriteLabel createSpriteLabel(String text, String fontName, float scale, Color color) {
        SpriteLabel label = createSpriteLabel(text, fontName, scale);
        if (label != null) {
            label.setColor(color);
        }
        return label;
    }
    
    /**
     * Creates a sprite-based label with alignment.
     * 
     * @param text The text to display
     * @param fontName The name of the loaded sprite font
     * @param alignment Text alignment (LEFT, CENTER, RIGHT)
     * @return A SpriteLabel widget
     */
    public static SpriteLabel createSpriteLabel(String text, String fontName, SpriteLabel.Align alignment) {
        SpriteLabel label = createSpriteLabel(text, fontName);
        if (label != null) {
            label.setAlignment(alignment);
        }
        return label;
    }
    
    /**
     * Creates a sprite-based label with full customization.
     * 
     * @param text The text to display
     * @param fontName The name of the loaded sprite font
     * @param scale Scale factor
     * @param color Color tint
     * @param alignment Text alignment
     * @param spacing Character spacing adjustment
     * @return A SpriteLabel widget
     */
    public static SpriteLabel createSpriteLabel(String text, String fontName, float scale, Color color, 
                                                 SpriteLabel.Align alignment, float spacing) {
        SpriteLabel label = createSpriteLabel(text, fontName, scale, color);
        if (label != null) {
            label.setAlignment(alignment);
            label.setSpacing(spacing);
        }
        return label;
    }

    // ========================================================================
    // 16. SPRITE-BASED BUTTONS (IMAGE-BASED FONTS)
    // ========================================================================
    
    /**
     * Creates a button with sprite-based text rendering (no .fnt files).
     * Returns a Container with a background drawable and SpriteLabel text.
     * 
     * @param text The button label text
     * @param fontName The name of the loaded sprite font
     * @param skin The UI skin for button background styling
     * @param onClick The click listener
     * @return A Container wrapping a button with sprite text
     * 
     * Example:
     * SpriteFontManager.load("default", "ui/ctm.uiskin.png");
     * Container btn = UIHelper.createSpriteButton("PLAY", "default", skin, listener);
     * table.add(btn).width(200).height(50).pad(10).row();
     */
    public static com.badlogic.gdx.scenes.scene2d.ui.Button createSpriteButton(String text, String fontName, Skin skin, ClickListener onClick) {
        SpriteFont font = SpriteFontManager.get(fontName);
        if (font == null) {
            Gdx.app.error("UIHelper", "Font '" + fontName + "' not loaded");
            return null;
        }
        
        // Create button with skin style
        com.badlogic.gdx.scenes.scene2d.ui.Button button = new com.badlogic.gdx.scenes.scene2d.ui.Button(
            skin.get(com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle.class)
        );
        
        // Create sprite label for text - scale will be adjusted dynamically
        final SpriteLabel label = new SpriteLabel(font, text, SPRITE_FONT_SCALE);
        label.setAlignment(SpriteLabel.Align.CENTER);
        button.add(label).center().expand().fill();
        button.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Add layout listener to dynamically scale font based on button size
        button.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener() {
            @Override
            public void touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, int button) {
                // Calculate optimal scale based on button dimensions
                float availableWidth = label.getWidth() - (BUTTON_PAD_X * 2);
                float availableHeight = label.getHeight() - (BUTTON_PAD_Y * 2);
                
                float textWidth = font.getWidth(text, 1f);
                float textHeight = font.getHeight(1f);
                
                if (textWidth > 0 && textHeight > 0) {
                    float scaleX = availableWidth / textWidth;
                    float scaleY = availableHeight / textHeight;
                    float optimalScale = Math.min(scaleX, scaleY) * 0.8f; // 80% to leave some margin
                    
                    if (optimalScale > 0.1f && optimalScale != label.getScale()) {
                        label.setScale(optimalScale);
                    }
                }
            }
        });
        
        // Attach click listener with sound
        if (onClick != null) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        return button;
    }
    
    // ========================================================================
    // 17. SPRITE BUTTON WITH MANUAL FONT SIZE CONTROL
    // ========================================================================
    
    /**
     * Creates a sprite button with manual font size control.
     * 
     * @param text The button label text
     * @param fontName The name of the loaded sprite font
     * @param fontScale The font scale (size) - use positive values for fixed size, 0 or negative for auto-scale
     * @param skin The UI skin for button background styling
     * @param onClick The click listener
     * @return A Button with sprite font text at specified size
     */
    public static com.badlogic.gdx.scenes.scene2d.ui.Button createSpriteButton(String text, String fontName, float fontScale, Skin skin, ClickListener onClick) {
        SpriteFont font = SpriteFontManager.get(fontName);
        if (font == null) {
            Gdx.app.error("UIHelper", "Font '" + fontName + "' not loaded");
            return null;
        }
        
        // Create button with skin style
        com.badlogic.gdx.scenes.scene2d.ui.Button button = new com.badlogic.gdx.scenes.scene2d.ui.Button(
            skin.get(com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle.class)
        );
        
        // Create sprite label with specified font scale
        final SpriteLabel label = new SpriteLabel(font, text, fontScale > 0 ? fontScale : SPRITE_FONT_SCALE);
        final boolean autoscale = fontScale <= 0;
        label.setAlignment(SpriteLabel.Align.CENTER);
        button.add(label).center().expand().fill();
        button.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Add layout listener for dynamic scaling if fontScale <= 0
        if (autoscale) {
            button.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener() {
                @Override
                public void touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, int button) {
                    float availableWidth = label.getWidth() - (BUTTON_PAD_X * 2);
                    float availableHeight = label.getHeight() - (BUTTON_PAD_Y * 2);
                    
                    float textWidth = font.getWidth(text, 1f);
                    float textHeight = font.getHeight(1f);
                    
                    if (textWidth > 0 && textHeight > 0) {
                        float scaleX = availableWidth / textWidth;
                        float scaleY = availableHeight / textHeight;
                        float optimalScale = Math.min(scaleX, scaleY) * 0.8f;
                        
                        if (optimalScale > 0.1f && optimalScale != label.getScale()) {
                            label.setScale(optimalScale);
                        }
                    }
                }
            });
        }
        
        // Attach click listener with sound
        if (onClick != null) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        return button;
    }
    
    /**
     * Creates a sprite button using the default sprite font, default scale and white color.
     * This overload avoids requiring callers to provide font name, scale or color.
     * 
     * @param text The button label text
     * @param skin The UI skin for button background styling
     * @param onClick The click listener
     * @return A Button with sprite font text using defaults
     */
    public static com.badlogic.gdx.scenes.scene2d.ui.Button createSpriteButton(String text, Skin skin, ClickListener onClick) {
        // Use the default sprite font name and default scale/color
        SpriteFont font = SpriteFontManager.get(DEFAULT_SPRITE_FONT);
        if (font == null) {
            Gdx.app.error("UIHelper", "Font '" + DEFAULT_SPRITE_FONT + "' not loaded");
            return null;
        }
        
        // Create button with skin style
        com.badlogic.gdx.scenes.scene2d.ui.Button button = new com.badlogic.gdx.scenes.scene2d.ui.Button(
            skin.get(com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle.class)
        );
        
        // Create sprite label with default font scale and white color
        final SpriteLabel label = new SpriteLabel(font, text, SPRITE_FONT_SCALE);
        final boolean autoscale = true; // autoscale by default for this overload
        label.setColor(Color.WHITE);
        label.setAlignment(SpriteLabel.Align.CENTER);
        button.add(label).center().expand().fill();
        button.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Add layout listener for dynamic scaling
        if (autoscale) {
            button.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener() {
                @Override
                public void touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, int button) {
                    float availableWidth = label.getWidth() - (BUTTON_PAD_X * 2);
                    float availableHeight = label.getHeight() - (BUTTON_PAD_Y * 2);
                    
                    float textWidth = font.getWidth(text, 1f);
                    float textHeight = font.getHeight(1f);
                    
                    if (textWidth > 0 && textHeight > 0) {
                        float scaleX = availableWidth / textWidth;
                        float scaleY = availableHeight / textHeight;
                        float optimalScale = Math.min(scaleX, scaleY) * 0.8f;
                        
                        if (optimalScale > 0.1f && optimalScale != label.getScale()) {
                            label.setScale(optimalScale);
                        }
                    }
                }
            });
        }
        
        // Attach click listener with sound
        if (onClick != null) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        return button;
    }

    // ========================================================================
    // 18. IMAGE BUTTON WITH STATE-BASED PNG BACKGROUNDS
    // ========================================================================
    
    /**
     * Creates an image-only button with custom PNGs for inactive, pressed, and hover states.
     * Each state can have its own PNG texture.
     * 
     * @param iconPath Path to the PNG for the button's icon (centered image)
     * @param inactivePath Path to the PNG for the inactive/normal state background
     * @param pressedPath Path to the PNG for the pressed state background
     * @param hoverPath Path to the PNG for the hover state background
     * @param skin The UI skin (for sizing, fallback, etc.)
     * @param onClick Click listener for button
     * @return A Container<Table> acting as an image button with state-based backgrounds
     */
    public static ImageButton createImageButton(String iconPath, String inactivePath, String pressedPath, String hoverPath, Skin skin, ClickListener onClick) {
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        
        // Create TextureRegionDrawables from paths with proper scaling
        TextureRegionDrawable upDrawable = new TextureRegionDrawable(new TextureRegion(new com.badlogic.gdx.graphics.Texture(Gdx.files.internal(inactivePath))));
        TextureRegionDrawable downDrawable = new TextureRegionDrawable(new TextureRegion(new com.badlogic.gdx.graphics.Texture(Gdx.files.internal(pressedPath))));
        TextureRegionDrawable overDrawable = new TextureRegionDrawable(new TextureRegion(new com.badlogic.gdx.graphics.Texture(Gdx.files.internal(hoverPath))));
        
        // Enable scaling for drawables
        upDrawable.setMinWidth(50);
        upDrawable.setMinHeight(50);
        downDrawable.setMinWidth(50);
        downDrawable.setMinHeight(50);
        overDrawable.setMinWidth(50);
        overDrawable.setMinHeight(50);
        
        style.up = upDrawable;
        style.down = downDrawable;
        style.over = overDrawable;
        
        // Create and configure the button
        ImageButton imageButton = new ImageButton(style);
        
        // Add click listener with sound
        if (onClick != null) {
            imageButton.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    io.github.ZaneStyx.Utils.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }

        return imageButton;
    }
}

