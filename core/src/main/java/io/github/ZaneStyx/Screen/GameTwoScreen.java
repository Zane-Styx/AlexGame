package io.github.ZaneStyx.Screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.ZaneStyx.Utils.Assets;
import io.github.ZaneStyx.Utils.PythonBackendManager;
import io.github.ZaneStyx.Utils.PythonBridgeClient;
import io.github.ZaneStyx.Utils.SpriteAnimator;
import io.github.ZaneStyx.Utils.UIHelper;

import java.util.Base64;

public class GameTwoScreen implements Screen {
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private Stage stage;
    private Skin skin;
    private PythonBridgeClient pythonClient;
    private Thread frameThread;
    private volatile boolean running;
    private volatile String latestFrameBase64;
    private String processedFrameBase64;
    private Texture cameraTexture;
    private final JsonReader jsonReader = new JsonReader();
    private static final float EXIT_BUTTON_WIDTH = 186f;
    private static final float EXIT_BUTTON_HEIGHT = 56f;
    
    // Bug Ninja game variables
    private Array<Bug> bugs;
    private SpriteAnimator bugAnimator;
    private float spawnTimer;
    private static final float SPAWN_INTERVAL = 1.5f;
    private static final float BUG_SIZE = 100f;
    private int score;
    
    // Hand tracking and sword
    private Texture swordTexture;
    private com.badlogic.gdx.graphics.g2d.TextureRegion swordRegion;
    private float swordX, swordY;
    private float swordRotation, swordTargetRotation;
    private float[] lastHandPos;
    private float[] lastSwordPos;
    private static final float SWORD_SIZE = 80f;
    private static final float SWORD_LERP_SPEED = 0.5f; // Smoothing factor (0-1)
    private float[] smoothedHandPos;
    private Array<float[]> currentLandmarks;
    private static final float HAND_SMOOTH_MIN = 0.08f;
    private static final float HAND_SMOOTH_MAX = 0.45f;
    private Array<HandTrailPoint> handTrail;
    private static final int MAX_TRAIL_POINTS = 50;
    
    // Hand trail point class
    private static class HandTrailPoint {
        float x, y;
        
        HandTrailPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
    
    // Bug class to represent each flying bug
    private static class Bug {
        float x, y;
        float velocityX, velocityY;
        String animName;
        boolean alive;
        Rectangle bounds;
        float rotation;
        static final float GRAVITY = -500f; // Pixels per second squared
        
        Bug(float x, float y, float velocityX, float velocityY, String animName) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.animName = animName;
            this.alive = true;
            this.rotation = 0f;
            this.bounds = new Rectangle(x, y, BUG_SIZE, BUG_SIZE);
        }
        
        void update(float delta) {
            // Apply gravity to vertical velocity
            velocityY += GRAVITY * delta;
            
            // Update position
            x += velocityX * delta;
            y += velocityY * delta;
            
            // Rotate based on velocity direction (natural spin)
            rotation = (float) Math.toDegrees(Math.atan2(velocityY, velocityX));
            
            bounds.set(x, y, BUG_SIZE, BUG_SIZE);
        }
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        shapeRenderer.setAutoShapeType(true);
        font = new BitmapFont();
        font.getData().setScale(2f);

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = Assets.manager.get("ui/uiskin.json", Skin.class);

        // Initialize bug ninja game
        bugs = new Array<>();
        handTrail = new Array<>();
        currentLandmarks = new Array<>();
        lastHandPos = null;
        smoothedHandPos = null;
        lastSwordPos = new float[]{Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f};
        swordX = lastSwordPos[0];
        swordY = lastSwordPos[1];
        swordRotation = 0f;
        swordTargetRotation = 0f;
        swordTexture = Assets.manager.get("game/sword.png", Texture.class);
        swordRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(swordTexture);
        bugAnimator = new SpriteAnimator("game/bugs.png", 4, 4);
        
        // Create animations for each bug type: 4 frames each
        bugAnimator.addAnimation("purple", 0, 0, 4, 0.1f, true);
        bugAnimator.addAnimation("green", 1, 0, 4, 0.1f, true);
        bugAnimator.addAnimation("blue", 2, 0, 4, 0.1f, true);
        bugAnimator.addAnimation("red", 3, 0, 4, 0.1f, true);
        
        spawnTimer = 0f;
        score = 0;

        // Create back button in top-left corner
        Table buttonTable = new Table();
        buttonTable.setFillParent(true);
        buttonTable.top().left().pad(20f);
        ImageButton backButton = createBackButton();
        buttonTable.add(backButton).width(EXIT_BUTTON_WIDTH).height(EXIT_BUTTON_HEIGHT);
        stage.addActor(buttonTable);

        // Start Python backend and camera feed
        PythonBackendManager.startServer();
        pythonClient = new PythonBridgeClient("127.0.0.1", 9009);
        startFrameThread();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.15f, 0.2f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        updateCameraTexture();
        updateGame(delta);

        batch.begin();
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        
        // Draw camera feed as background (smaller, semi-transparent)
        if (cameraTexture != null) {
            batch.setColor(1f, 1f, 1f, 0.3f);
            batch.draw(cameraTexture, 0, 0, screenWidth, screenHeight);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        
        // Draw bugs
        for (Bug bug : bugs) {
            if (bug.alive) {
                bugAnimator.play(bug.animName, false);
                bugAnimator.update(0.016f); // Update animation each frame
                com.badlogic.gdx.graphics.g2d.TextureRegion frame = bugAnimator.getCurrentFrameRegion();
                if (frame != null) {
                    batch.draw(frame, bug.x, bug.y, BUG_SIZE / 2f, BUG_SIZE / 2f, BUG_SIZE, BUG_SIZE, 1f, 1f, bug.rotation);
                }
            }
        }
        batch.end();

        // Draw dagger trail
        if (handTrail.size > 1) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 1; i < handTrail.size; i++) {
                HandTrailPoint prev = handTrail.get(i - 1);
                HandTrailPoint curr = handTrail.get(i);
                float t = (float) i / (float) (handTrail.size - 1);
                float alpha = 0.6f * t;
                shapeRenderer.setColor(1f, 1f, 1f, alpha);
                shapeRenderer.rectLine(prev.x, prev.y, curr.x, curr.y, 6f);
            }
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        
        // Draw hand landmarks (temporary debug)
        if (currentLandmarks.size > 0) {
            screenWidth = Gdx.graphics.getWidth();
            screenHeight = Gdx.graphics.getHeight();
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0f, 1f, 0f, 0.8f);
            for (float[] lm : currentLandmarks) {
                float x = lm[0] * screenWidth;
                float y = (1f - lm[1]) * screenHeight;
                shapeRenderer.circle(x, y, 5f);
            }
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        
        // Draw sword following hand
        if (swordRegion != null) {
            batch.begin();
            batch.draw(swordRegion, swordX - SWORD_SIZE / 2f, swordY - SWORD_SIZE / 2f, SWORD_SIZE / 2f, SWORD_SIZE / 2f, 
                      SWORD_SIZE, SWORD_SIZE, 1f, 1f, swordRotation);
            batch.end();
        }
        
        batch.begin();
        // Draw UI
        font.draw(batch, "Score: " + score, 20, screenHeight - 80);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        running = false;
        if (frameThread != null) {
            try {
                frameThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        running = false;
        if (frameThread != null) {
            try {
                frameThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
        try { if (pythonClient != null) pythonClient.close(); } catch (Exception ignored) {}
        try { if (swordTexture != null) swordTexture.dispose(); } catch (Exception ignored) {}
        try { if (cameraTexture != null) cameraTexture.dispose(); } catch (Exception ignored) {}
        try { if (bugAnimator != null) bugAnimator.dispose(); } catch (Exception ignored) {}
        try { if (shapeRenderer != null) shapeRenderer.dispose(); } catch (Exception ignored) {}
        try { if (batch != null) batch.dispose(); } catch (Exception ignored) {}
        try { if (font != null) font.dispose(); } catch (Exception ignored) {}
        try { if (stage != null) stage.dispose(); } catch (Exception ignored) {}
    }
    
    private void updateGame(float delta) {
        // Spawn new bugs
        spawnTimer += delta;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            spawnBug();
        }
        
        // Update bugs
        float screenHeight = Gdx.graphics.getHeight();
        float screenWidth = Gdx.graphics.getWidth();
        
        for (int i = bugs.size - 1; i >= 0; i--) {
            Bug bug = bugs.get(i);
            bug.update(delta);
            
            // Remove bugs that went off-screen (fell below or went to sides)
            if (bug.y < -BUG_SIZE || bug.x < -BUG_SIZE || bug.x > screenWidth + BUG_SIZE) {
                bugs.removeIndex(i);
            }
        }
        
        // Update sword position with smooth lerp
        if (lastHandPos != null) {
            swordX = lerp(swordX, lastHandPos[0], SWORD_LERP_SPEED);
            swordY = lerp(swordY, lastHandPos[1], SWORD_LERP_SPEED);
            
            // Calculate target rotation based on hand movement direction
            float dx = lastHandPos[0] - swordX;
            float dy = lastHandPos[1] - swordY;
            swordTargetRotation = (float) Math.toDegrees(Math.atan2(dy, dx));
            
            // Lerp rotation smoothly (accounting for 360 degree wrap)
            swordRotation = lerpAngle(swordRotation, swordTargetRotation, 0.15f);
        }
        
        // Check for hand collision with bugs
        checkHandBugCollisions();
    }
    
    private float lerp(float current, float target, float factor) {
        return current + (target - current) * factor;
    }
    
    private float lerpAngle(float current, float target, float factor) {
        // Normalize angles to -180 to 180 range
        float diff = target - current;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return current + diff * factor;
    }
    
    private void checkHandBugCollisions() {
        if (lastHandPos == null) {
            return;
        }
        
        float handX = lastHandPos[0];
        float handY = lastHandPos[1];
        float handRadius = 30f;
        
        for (int i = bugs.size - 1; i >= 0; i--) {
            Bug bug = bugs.get(i);
            if (!bug.alive) continue;
            
            // Check if hand is within bug bounds
            if (bug.bounds.contains(handX, handY) || bug.bounds.contains(handX + handRadius, handY)) {
                bug.alive = false;
                score += 10;
                // Remove the sliced bug
                bugs.removeIndex(i);
            }
        }
    }
    
    private void spawnBug() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        
        // Spawn from bottom or sides
        float x, y, velocityX, velocityY;
        
        if (MathUtils.randomBoolean()) {
            // Spawn from bottom
            x = MathUtils.random(0f, screenWidth);
            y = -BUG_SIZE;
            velocityX = MathUtils.random(-200f, 200f);
            velocityY = MathUtils.random(600f, 900f);
        } else {
            // Spawn from sides
            if (MathUtils.randomBoolean()) {
                // Left side
                x = -BUG_SIZE;
                velocityX = MathUtils.random(200f, 400f);
            } else {
                // Right side
                x = screenWidth + BUG_SIZE;
                velocityX = MathUtils.random(-400f, -200f);
            }
            y = MathUtils.random(0f, screenHeight * 0.5f);
            velocityY = MathUtils.random(500f, 800f);
        }
        
        int bugTypeIndex = MathUtils.random(0, 3); // Random bug type: 0=purple, 1=green, 2=blue, 3=red
        String[] bugTypes = {"purple", "green", "blue", "red"};
        String animName = bugTypes[bugTypeIndex];
        bugs.add(new Bug(x, y, velocityX, velocityY, animName));
    }

    private ImageButton createBackButton() {
        return UIHelper.createImageButton(
            "ui/backBtn/BackBtn_0.png",
            "ui/backBtn/BackBtn_0.png",
            "ui/backBtn/BackBtn_1.png",
            "ui/backBtn/BackBtn_Hover.png",
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    Game game = (Game) Gdx.app.getApplicationListener();
                    game.setScreen(new MainMenuScreen(game));
                }
            }
        );
    }

    private void startFrameThread() {
        running = true;
        frameThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean pythonConnected = false;
                while (running) {
                    if (!pythonConnected) {
                        try {
                            pythonClient.connect(1000);
                            pythonConnected = true;
                        } catch (Exception e) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ignored) {
                                break;
                            }
                            continue;
                        }
                    }

                    try {
                        String payload = "{\"camera_id\":0,\"quality\":35,\"max_width\":320,\"max_height\":240}";  // Smaller frames for smoother performance
                        String response = pythonClient.sendRequest("get_frame", payload);
                        if (response != null) {
                            JsonValue root = jsonReader.parse(response);
                            if (root.getBoolean("ok", false)) {
                                JsonValue data = root.get("data");
                                if (data != null) {
                                    String b64 = data.getString("image_b64", null);
                                    if (b64 != null) {
                                        latestFrameBase64 = b64;
                                    }
                                    
                                    // Extract hand landmarks
                                    JsonValue landmarks = data.get("hand_landmarks");
                                    if (landmarks != null && landmarks.isArray() && landmarks.size > 0) {
                                        // Store landmarks for visualization
                                        currentLandmarks.clear();
                                        for (int i = 0; i < landmarks.size; i++) {
                                            JsonValue lm = landmarks.get(i);
                                            if (lm != null && lm.isArray() && lm.size >= 2) {
                                                currentLandmarks.add(new float[]{lm.getFloat(0), lm.getFloat(1)});
                                            }
                                        }
                                        // Use middle finger tip (landmark 9) as hand position
                                        if (landmarks.size > 9) {
                                            JsonValue midFingerTip = landmarks.get(9);
                                            if (midFingerTip != null && midFingerTip.isArray() && midFingerTip.size >= 2) {
                                                int frameWidth = data.getInt("width", 640);
                                                int frameHeight = data.getInt("height", 480);
                                                float screenWidth = Gdx.graphics.getWidth();
                                                float screenHeight = Gdx.graphics.getHeight();
                                                
                                                // Convert normalized coordinates to screen coordinates
                                                float normalizedX = midFingerTip.getFloat(0);
                                                float normalizedY = midFingerTip.getFloat(1);
                                                float screenX = normalizedX * screenWidth;
                                                float screenY = (1f - normalizedY) * screenHeight;
                                                
                                                if (smoothedHandPos == null) {
                                                    smoothedHandPos = new float[]{screenX, screenY};
                                                } else {
                                                    float dx = screenX - smoothedHandPos[0];
                                                    float dy = screenY - smoothedHandPos[1];
                                                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                                                    float adaptiveFactor = MathUtils.clamp(HAND_SMOOTH_MIN + (dist / 300f) * (HAND_SMOOTH_MAX - HAND_SMOOTH_MIN), HAND_SMOOTH_MIN, HAND_SMOOTH_MAX);
                                                    smoothedHandPos[0] = lerp(smoothedHandPos[0], screenX, adaptiveFactor);
                                                    smoothedHandPos[1] = lerp(smoothedHandPos[1], screenY, adaptiveFactor);
                                                }

                                                lastHandPos = new float[]{smoothedHandPos[0], smoothedHandPos[1]};
                                                
                                                // Add to trail if moved enough
                                                if (handTrail.size == 0 || 
                                                    Math.abs(handTrail.peek().x - lastHandPos[0]) > 5 || 
                                                    Math.abs(handTrail.peek().y - lastHandPos[1]) > 5) {
                                                    handTrail.add(new HandTrailPoint(lastHandPos[0], lastHandPos[1]));
                                                    if (handTrail.size > MAX_TRAIL_POINTS) {
                                                        handTrail.removeIndex(0);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Thread.sleep(33); // ~30 FPS
                    } catch (Exception e) {
                        pythonConnected = false;
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                }
            }
        }, "GameTwo-FrameThread");
        frameThread.setDaemon(true);
        frameThread.start();
    }

    private void updateCameraTexture() {
        String currentBase64 = latestFrameBase64;
        if (currentBase64 == null || currentBase64.equals(processedFrameBase64)) {
            return;
        }

        try {
            byte[] rawBytes = Base64.getDecoder().decode(currentBase64);
            Pixmap pixmap = new Pixmap(rawBytes, 0, rawBytes.length);

            if (cameraTexture == null || cameraTexture.getWidth() != pixmap.getWidth() || cameraTexture.getHeight() != pixmap.getHeight()) {
                if (cameraTexture != null) {
                    cameraTexture.dispose();
                }
                cameraTexture = new Texture(pixmap);
            } else {
                cameraTexture.draw(pixmap, 0, 0);
            }
            pixmap.dispose();
            processedFrameBase64 = currentBase64;
        } catch (Exception e) {
            Gdx.app.error("GameTwoScreen", "Failed to decode camera frame", e);
        }
    }
}
