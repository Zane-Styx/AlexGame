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
    
    // Video display constants
    private static final float VIDEO_BORDER = 50f;
    private float videoX, videoY, videoWidth, videoHeight;
    
    // Bug Ninja game variables
    private Array<Bug> bugs;
    private SpriteAnimator bugAnimator;
    private float spawnTimer;
    private static final float SPAWN_INTERVAL = 1.5f;
    private static final float BUG_SIZE = 100f;
    private int score;
    private int highScore;
    private int bugsSliced;
    private int bugsMissed;
    private long lastSliceTime;
    private int comboCount;
    private float comboMultiplier;
    private static final long COMBO_WINDOW_MS = 2000;
    
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
    private static final boolean DRAW_DEBUG_LANDMARKS = false; // Disabled for performance
    
    // Loading state
    private volatile boolean cameraReady = false;
    private volatile String loadingMessage = "Initializing camera...";
    
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
        static final float GRAVITY = -160f; // Pixels per second squared (slower so bugs stay in play longer)
        
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

        // Bounce off the given video-area walls (top, left, right only).
        // Returns true if the bug has fallen below the bottom edge and should be removed.
        boolean bounceAndCheckEscape(float vx, float vy, float vw, float vh) {
            // Left wall
            if (x < vx) {
                x = vx;
                velocityX = Math.abs(velocityX);
            }
            // Right wall
            if (x + BUG_SIZE > vx + vw) {
                x = vx + vw - BUG_SIZE;
                velocityX = -Math.abs(velocityX);
            }
            // Top wall
            if (y + BUG_SIZE > vy + vh) {
                y = vy + vh - BUG_SIZE;
                velocityY = -Math.abs(velocityY);
            }
            // Bottom edge — bug escaped
            return y + BUG_SIZE < vy;
        }
        
        int getPointValue() {
            switch (animName) {
                case "red": return 50;
                case "blue": return 30;
                case "green": return 20;
                case "purple": return 10;
                default: return 10;
            }
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
        highScore = 0;
        bugsSliced = 0;
        bugsMissed = 0;
        lastSliceTime = 0;
        comboCount = 0;
        comboMultiplier = 1.0f;

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
        
        // Show loading screen while camera initializes
        if (!cameraReady) {
            batch.begin();
            font.getData().setScale(2.0f);
            float textX = Gdx.graphics.getWidth() / 2f - 150f;
            float textY = Gdx.graphics.getHeight() / 2f;
            font.draw(batch, loadingMessage, textX, textY);
            
            // Show animated dots
            int dots = (int)((System.currentTimeMillis() / 500) % 4);
            String dotString = "";
            for (int i = 0; i < dots; i++) {
                dotString += ".";
            }
            font.draw(batch, dotString, textX + 300f, textY);
            
            font.getData().setScale(1.0f);
            font.draw(batch, "Please wait...", textX + 20f, textY - 50f);
            batch.end();
            
            stage.act(delta);
            stage.draw();
            return;
        }
        
        // Calculate video display area with 100px border
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        calculateVideoArea(screenWidth, screenHeight);
        
        updateGame(delta);

        batch.begin();
        
        // Draw camera feed scaled with 100px border, maintaining aspect ratio
        if (cameraTexture != null) {
            batch.setColor(1f, 1f, 1f, 0.3f);
            batch.draw(cameraTexture, videoX, videoY, videoWidth, videoHeight);
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

        // Draw hand trail and sword with single ShapeRenderer pass
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        
        // Draw hand trail
        if (handTrail.size > 1) {
            for (int i = 1; i < handTrail.size; i++) {
                HandTrailPoint prev = handTrail.get(i - 1);
                HandTrailPoint curr = handTrail.get(i);
                float t = (float) i / (float) (handTrail.size - 1);
                float alpha = 0.6f * t;
                shapeRenderer.setColor(1f, 1f, 1f, alpha);
                shapeRenderer.rectLine(prev.x, prev.y, curr.x, curr.y, 6f);
            }
        }
        
        // Draw hand landmarks only if debug enabled
        if (DRAW_DEBUG_LANDMARKS && currentLandmarks.size > 0) {
            shapeRenderer.setColor(0f, 1f, 0f, 0.8f);
            for (float[] lm : currentLandmarks) {
                float x = videoX + (lm[0] * videoWidth);
                float y = videoY + ((1f - lm[1]) * videoHeight);
                shapeRenderer.circle(x, y, 5f);
            }
        }
        
        shapeRenderer.end();
        
        // Draw border with line mode
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f);
        Gdx.gl.glLineWidth(3f);
        shapeRenderer.rect(videoX, videoY, videoWidth, videoHeight);
        shapeRenderer.end();
        
        Gdx.gl.glDisable(GL20.GL_BLEND);
        
        // Draw sword following hand
        if (swordRegion != null) {
            batch.begin();
            batch.draw(swordRegion, swordX - SWORD_SIZE / 2f, swordY - SWORD_SIZE / 2f, SWORD_SIZE / 2f, SWORD_SIZE / 2f, 
                      SWORD_SIZE, SWORD_SIZE, 1f, 1f, swordRotation);
            batch.end();
        }
        
        batch.begin();
        // Draw UI
        font.getData().setScale(2.0f);
        font.draw(batch, "Score: " + score, 20, screenHeight - 80);
        font.getData().setScale(1.5f);
        if (highScore > 0) {
            font.draw(batch, "High: " + highScore, 20, screenHeight - 120);
        }
        if (comboCount > 1) {
            font.draw(batch, "Combo: x" + comboCount + " (" + String.format("%.1f", comboMultiplier) + "x)", 20, screenHeight - 160);
        }
        font.getData().setScale(1.2f);
        font.draw(batch, "Sliced: " + bugsSliced + " | Missed: " + bugsMissed, 20, screenHeight - 200);
        font.getData().setScale(1.0f);
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
        for (int i = bugs.size - 1; i >= 0; i--) {
            Bug bug = bugs.get(i);
            bug.update(delta);

            // Bounce off top / left / right video borders; escape only from the bottom.
            boolean escaped = bug.bounceAndCheckEscape(videoX, videoY, videoWidth, videoHeight);
            if (escaped) {
                if (bug.alive) {
                    bugsMissed++;
                    score = Math.max(0, score - 10);
                    comboCount = 0;
                    comboMultiplier = 1.0f;
                }
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
        // Use the sword sprite center as the hit point — the sword is the weapon, not the raw hand.
        // swordX/swordY track a smoothed version of the hand already, so this is always valid.
        float swordHitX = swordX;
        float swordHitY = swordY;
        float swordHitRadius = SWORD_SIZE / 2f; // 40 px — matches the visible sword sprite

        long currentTime = System.currentTimeMillis();
        boolean slicedAnyBug = false;
        
        for (int i = bugs.size - 1; i >= 0; i--) {
            Bug bug = bugs.get(i);
            if (!bug.alive) continue;
            
            // Check if the sword hit circle overlaps the bug rectangle.
            // Clamp sword center to the bug rect, then test distance vs. radius.
            float nearestX = Math.max(bug.bounds.x, Math.min(swordHitX, bug.bounds.x + bug.bounds.width));
            float nearestY = Math.max(bug.bounds.y, Math.min(swordHitY, bug.bounds.y + bug.bounds.height));
            float dx = swordHitX - nearestX;
            float dy = swordHitY - nearestY;
            boolean hit = (dx * dx + dy * dy) <= (swordHitRadius * swordHitRadius);
            if (hit) {
                bug.alive = false;
                slicedAnyBug = true;
                bugsSliced++;
                
                // Check for combo
                if (lastSliceTime > 0 && (currentTime - lastSliceTime) < COMBO_WINDOW_MS) {
                    comboCount++;
                } else {
                    comboCount = 1;
                }
                
                // Calculate combo multiplier
                if (comboCount >= 5) {
                    comboMultiplier = 3.0f;
                } else if (comboCount >= 3) {
                    comboMultiplier = 2.0f;
                } else {
                    comboMultiplier = 1.0f;
                }
                
                lastSliceTime = currentTime;
                
                // Award points based on bug type and combo
                int points = (int)(bug.getPointValue() * comboMultiplier);
                score += points;
                
                // Update high score
                if (score > highScore) {
                    highScore = score;
                }
                
                // Remove the sliced bug
                bugs.removeIndex(i);
            }
        }
        
        // Reset combo if no bugs sliced
        if (!slicedAnyBug && lastSliceTime > 0 && (currentTime - lastSliceTime) > COMBO_WINDOW_MS) {
            comboCount = 0;
            comboMultiplier = 1.0f;
        }
    }
    
    private void spawnBug() {
        // Spawn at edges of the video area (bugs fly INTO the video area)
        float x, y, velocityX, velocityY;
        
        // All bugs spawn from the bottom edge and bounce inside the video area.
        // Velocities are intentionally lower than before so they move at a comfortable pace.
        x = MathUtils.random(videoX, videoX + videoWidth - BUG_SIZE);
        y = videoY;
        velocityX = MathUtils.random(-150f, 150f);
        velocityY = MathUtils.random(280f, 420f);
        
        int bugTypeIndex = MathUtils.random(0, 3); // Random bug type: 0=purple, 1=green, 2=blue, 3=red
        String[] bugTypes = {"purple", "green", "blue", "red"};
        String animName = bugTypes[bugTypeIndex];
        bugs.add(new Bug(x, y, velocityX, velocityY, animName));
    }

    private void calculateVideoArea(float screenWidth, float screenHeight) {
        // Calculate available area with border
        float availableWidth = screenWidth - (VIDEO_BORDER * 2);
        float availableHeight = screenHeight - (VIDEO_BORDER * 2);
        
        // Assume camera aspect ratio is 4:3 or 16:9, use texture if available
        float videoAspect = 4f / 3f; // Default aspect ratio
        if (cameraTexture != null) {
            videoAspect = (float) cameraTexture.getWidth() / (float) cameraTexture.getHeight();
        }
        
        // Calculate video dimensions maintaining aspect ratio
        float availableAspect = availableWidth / availableHeight;
        
        if (availableAspect > videoAspect) {
            // Available area is wider, fit to height
            videoHeight = availableHeight;
            videoWidth = videoHeight * videoAspect;
        } else {
            // Available area is taller, fit to width
            videoWidth = availableWidth;
            videoHeight = videoWidth / videoAspect;
        }
        
        // Center the video in the screen
        videoX = (screenWidth - videoWidth) / 2f;
        videoY = (screenHeight - videoHeight) / 2f;
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
                        String payload = "{\"camera_id\":0,\"quality\":50,\"max_width\":480,\"max_height\":360}";  // Balanced quality
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
                                        // Store landmarks for visualization (only if debug enabled)
                                        if (DRAW_DEBUG_LANDMARKS) {
                                            currentLandmarks.clear();
                                            for (int i = 0; i < landmarks.size; i++) {
                                                JsonValue lm = landmarks.get(i);
                                                if (lm != null && lm.isArray() && lm.size >= 2) {
                                                    currentLandmarks.add(new float[]{lm.getFloat(0), lm.getFloat(1)});
                                                }
                                            }
                                        }
                                        // Use middle finger tip (landmark 9) as hand position
                                        if (landmarks.size > 9) {
                                            JsonValue midFingerTip = landmarks.get(9);
                                            if (midFingerTip != null && midFingerTip.isArray() && midFingerTip.size >= 2) {
                                                // Convert normalized coordinates (0-1) to video area coordinates
                                                float normalizedX = midFingerTip.getFloat(0);
                                                float normalizedY = midFingerTip.getFloat(1);
                                                
                                                // Map to video display area (within 100px border)
                                                float screenX = videoX + (normalizedX * videoWidth);
                                                float screenY = videoY + ((1f - normalizedY) * videoHeight);
                                                
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
                        Thread.sleep(16); // ~60 FPS to match game one
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
            
            // Mark camera as ready once first frame is received
            if (!cameraReady) {
                cameraReady = true;
                loadingMessage = "Camera ready!";
            }
        } catch (Exception e) {
            Gdx.app.error("GameTwoScreen", "Failed to decode camera frame", e);
        }
    }
}
