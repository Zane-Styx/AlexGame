package io.github.ZaneStyx.Screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.ZaneStyx.Utils.Assets;
import io.github.ZaneStyx.Utils.PythonBackendManager;
import io.github.ZaneStyx.Utils.PythonBridgeClient;
import io.github.ZaneStyx.Utils.SpriteFontManager;
import io.github.ZaneStyx.Utils.SpriteLabel;
import io.github.ZaneStyx.Utils.UIHelper;

import java.util.Base64;

public class GameOneScreen implements Screen {
    private SpriteBatch batch;
    private Stage stage;
    private Skin skin;
    private PythonBridgeClient pythonClient;
    private Thread frameThread;
    private volatile boolean running;
    private volatile String latestFrameBase64;
    private String processedFrameBase64;
    private Texture cameraTexture;
    private Table uiPanel;
    private Table hudTable;
    private Table loadingTable;
    private Table sequenceIconsTable;
    private SpriteLabel scoreLabel;
    private SpriteLabel timerLabel;
    private SpriteLabel comboLabel;
    private SpriteLabel accuracyLabel;
    private SpriteLabel statusLabel;
    private SpriteLabel sequenceLabel;
    private SpriteLabel expectedLabel;
    private SpriteLabel progressLabel;
    private SpriteLabel loadingLabel;
    private SpriteLabel loadingDotsLabel;
    private SpriteLabel loadingHintLabel;
    private java.util.HashMap<String, Texture> gestureTextures;
    private volatile String expectedGestureName = null;
    private final JsonReader jsonReader = new JsonReader();
    private static final float EXIT_BUTTON_WIDTH = 186f;
    private static final float EXIT_BUTTON_HEIGHT = 56f;
    private volatile boolean gameLogicReady;
    private volatile String gameStatusMessage = "Starting Python game logic...";
    private volatile String gameSequenceText = "";
    private volatile String gameExpectedText = "";
    private volatile String gameProgressText = "";
    private volatile int lastGestureId = -1;
    private volatile long lastGestureTimeMs = 0L;
    private volatile int score = 0;
    private volatile int consecutiveCorrect = 0;
    private volatile int totalGestures = 0;
    private volatile int correctGestures = 0;
    private volatile float comboMultiplier = 1.0f;
    private volatile long roundStartTime = 0L;
    private volatile boolean cameraReady = false;
    private volatile String loadingMessage = "Initializing camera...";
    private volatile float timeRemaining = 15.0f;
    private volatile long lastTimerCheckMs = 0L;
    private static final long TIMER_CHECK_INTERVAL_MS = 100L;  // Check timer every 100ms
    private volatile long lastCorrectGestureTimeMs = 0L;
    private static final long CORRECT_GESTURE_COOLDOWN_MS = 1500L;  // Breathing time after correct gesture
    private volatile long lastReconnectAttemptMs = 0L;
    private static final long RECONNECT_INTERVAL_MS = 2000L;
    private static final float HUD_RIGHT_WIDTH = 460f;
    private static final float HUD_TOP_PAD = 340f;
    private static final float LOADING_TOP_PAD = 160f;
    private static final float SEQUENCE_ICON_SIZE = 44f;
    private static final float SEQUENCE_ICON_PAD = 6f;

    // Gesture ID to name mapping (matches Python backend)
    private String getGestureName(int gestureId) {
        switch (gestureId) {
            case 1: return "peace";
            case 2: return "ok";
            case 4: return "highfive";
            case 5: return "fist";
            case 6: return "point";
            case 7: return "rock";
            case 10: return "iloveyou";
            default: return null;
        }
    }
    
    private String getGestureDisplayName(int gestureId) {
        switch (gestureId) {
            case 1: return "Peace";
            case 2: return "OK";
            case 4: return "HighFive";
            case 5: return "Fist";
            case 6: return "Point";
            case 7: return "Rock";
            case 10: return "ILoveYou";
            default: return "Unknown";
        }
    }

    @Override
    public void show() {
        batch = new SpriteBatch();

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = Assets.manager.get("ui/uiskin.json", Skin.class);

        if (!SpriteFontManager.isLoaded(UIHelper.DEFAULT_SPRITE_FONT)) {
            SpriteFontManager.load(UIHelper.DEFAULT_SPRITE_FONT, "ui/ctm.uiskin.png");
        }

        // Load gesture textures
        gestureTextures = new java.util.HashMap<>();
        gestureTextures.put("fist", Assets.manager.get("game/gesture/fist.png", Texture.class));
        gestureTextures.put("highfive", Assets.manager.get("game/gesture/highfive.png", Texture.class));
        gestureTextures.put("iloveyou", Assets.manager.get("game/gesture/iloveyou.png", Texture.class));
        gestureTextures.put("ok", Assets.manager.get("game/gesture/ok.png", Texture.class));
        gestureTextures.put("peace", Assets.manager.get("game/gesture/peace.png", Texture.class));
        gestureTextures.put("point", Assets.manager.get("game/gesture/point.png", Texture.class));
        gestureTextures.put("rock", Assets.manager.get("game/gesture/rock.png", Texture.class));

        // Create back button in top-left corner
        uiPanel = new Table();
        uiPanel.setFillParent(true);
        uiPanel.top().right().pad(20f);
        ImageButton backButton = createBackButton();
        uiPanel.add(backButton).width(EXIT_BUTTON_WIDTH).height(EXIT_BUTTON_HEIGHT);
        stage.addActor(uiPanel);

        buildHud();
        buildLoadingUi();

        PythonBackendManager.startServer();
        pythonClient = new PythonBridgeClient("127.0.0.1", 9009);
        initializeGameLogic();
        startFrameThread();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        updateCameraTexture();
        checkTimerStatus();

        updateHudText();
        if (loadingTable != null) {
            loadingTable.setVisible(!cameraReady);
        }
        if (hudTable != null) {
            hudTable.setVisible(cameraReady);
        }

        // Show loading screen while camera initializes
        if (!cameraReady) {
            stage.act(delta);
            stage.draw();
            return;
        }

        batch.begin();
        if (cameraTexture != null) {
            float targetSize = 704f;
            float x = 32f;
            float y = Gdx.graphics.getHeight() - 32f - targetSize;
            int srcW = cameraTexture.getWidth();
            int srcH = cameraTexture.getHeight();
            int cropSize = Math.min(srcW, srcH);
            int srcX = (srcW - cropSize) / 2;
            int srcY = (srcH - cropSize) / 2;
            batch.draw(cameraTexture, x, y, targetSize, targetSize, srcX, srcY, cropSize, cropSize, false, false);
        } else {
            // No camera frame yet; HUD will show status on the right.
        }
        
        // Draw expected gesture image
        if (expectedGestureName != null && gestureTextures.containsKey(expectedGestureName.toLowerCase())) {
            Texture gestureTexture = gestureTextures.get(expectedGestureName.toLowerCase());
            float gestureSize = 256f;
            float gestureX = Gdx.graphics.getWidth() - gestureSize - 50f;
            float gestureY = Gdx.graphics.getHeight() - gestureSize - 50f;
            batch.draw(gestureTexture, gestureX, gestureY, gestureSize, gestureSize);
        }
        
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
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
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
        stopFrameThread();
        try { if (pythonClient != null) pythonClient.close(); } catch (Exception ignored) {}
        PythonBackendManager.stopServer();
    }

    @Override
    public void dispose() {
        try { if (batch != null) batch.dispose(); } catch (Exception ignored) {}
        try { if (stage != null) stage.dispose(); } catch (Exception ignored) {}
        try { if (cameraTexture != null) cameraTexture.dispose(); } catch (Exception ignored) {}
        try { if (pythonClient != null) pythonClient.close(); } catch (Exception ignored) {}
        PythonBackendManager.stopServer();
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

    private void buildHud() {
        hudTable = new Table();
        hudTable.setFillParent(true);
        hudTable.top().right().padTop(HUD_TOP_PAD).padRight(20f);

        scoreLabel = createRightLabel("Score: 0", 2.6f);
        timerLabel = createRightLabel("Time: 15s", 3.0f);
        comboLabel = createRightLabel("", 2.0f);
        accuracyLabel = createRightLabel("", 2.0f);
        statusLabel = createRightLabel("", 1.4f);
        sequenceLabel = createRightLabel("", 1.4f);
        expectedLabel = createRightLabel("", 1.4f);
        progressLabel = createRightLabel("", 1.4f);

        sequenceIconsTable = new Table();
        sequenceIconsTable.right();

        hudTable.add(scoreLabel).right().width(HUD_RIGHT_WIDTH).padBottom(10f).row();
        hudTable.add(timerLabel).right().width(HUD_RIGHT_WIDTH).padBottom(10f).row();
        hudTable.add(comboLabel).right().width(HUD_RIGHT_WIDTH).padBottom(6f).row();
        hudTable.add(accuracyLabel).right().width(HUD_RIGHT_WIDTH).padBottom(12f).row();
        hudTable.add(statusLabel).right().width(HUD_RIGHT_WIDTH).padBottom(6f).row();
        hudTable.add(sequenceLabel).right().width(HUD_RIGHT_WIDTH).padBottom(4f).row();
        hudTable.add(sequenceIconsTable).right().width(HUD_RIGHT_WIDTH).padBottom(8f).row();
        hudTable.add(expectedLabel).right().width(HUD_RIGHT_WIDTH).padBottom(6f).row();
        hudTable.add(progressLabel).right().width(HUD_RIGHT_WIDTH).padBottom(6f).row();

        stage.addActor(hudTable);
    }

    private void buildLoadingUi() {
        loadingTable = new Table();
        loadingTable.setFillParent(true);
        loadingTable.top().right().padTop(LOADING_TOP_PAD).padRight(20f);

        loadingLabel = createRightLabel(loadingMessage, 2.6f);
        loadingDotsLabel = createRightLabel("", 2.6f);
        loadingHintLabel = createRightLabel("Please wait...", 1.4f);

        loadingTable.add(loadingLabel).right().width(HUD_RIGHT_WIDTH).padBottom(6f).row();
        loadingTable.add(loadingDotsLabel).right().width(HUD_RIGHT_WIDTH).padBottom(12f).row();
        loadingTable.add(loadingHintLabel).right().width(HUD_RIGHT_WIDTH).padBottom(6f).row();

        stage.addActor(loadingTable);
    }

    private SpriteLabel createRightLabel(String text, float scale) {
        SpriteLabel label = UIHelper.createSpriteLabel(text, UIHelper.DEFAULT_SPRITE_FONT, scale);
        if (label != null) {
            label.setAlignment(SpriteLabel.Align.RIGHT);
            label.setSize(HUD_RIGHT_WIDTH, label.getPrefHeight());
        }
        return label;
    }

    private void lockHudWidth(SpriteLabel label) {
        if (label != null) {
            label.setSize(HUD_RIGHT_WIDTH, label.getPrefHeight());
        }
    }

    private void updateHudText() {
        if (scoreLabel != null) {
            scoreLabel.setText("Score: " + score);
            lockHudWidth(scoreLabel);
        }
        if (timerLabel != null) {
            int timeInt = (int) Math.ceil(timeRemaining);
            timerLabel.setText("Time: " + timeInt + "s");
            lockHudWidth(timerLabel);
        }
        if (comboLabel != null) {
            comboLabel.setText(comboMultiplier > 1.0f ? "Combo: x" + String.format("%.1f", comboMultiplier) : "");
            lockHudWidth(comboLabel);
        }
        if (accuracyLabel != null) {
            accuracyLabel.setText((correctGestures > 0 || totalGestures > 0) ?
                "Accuracy: " + correctGestures + "/" + totalGestures : "");
            lockHudWidth(accuracyLabel);
        }
        if (statusLabel != null) {
            statusLabel.setText("");
            lockHudWidth(statusLabel);
        }
        if (sequenceLabel != null) {
            sequenceLabel.setText("Sequence:");
            lockHudWidth(sequenceLabel);
        }
        if (expectedLabel != null) {
            expectedLabel.setText(gameExpectedText != null ? gameExpectedText : "");
            lockHudWidth(expectedLabel);
        }
        if (progressLabel != null) {
            progressLabel.setText(gameProgressText != null ? gameProgressText : "");
            lockHudWidth(progressLabel);
        }
        if (loadingLabel != null) {
            loadingLabel.setText(loadingMessage != null ? loadingMessage : "");
            lockHudWidth(loadingLabel);
        }
        if (loadingDotsLabel != null) {
            int dots = (int) ((System.currentTimeMillis() / 500) % 4);
            StringBuilder dotString = new StringBuilder();
            for (int i = 0; i < dots; i++) {
                dotString.append(".");
            }
            loadingDotsLabel.setText(dotString.toString());
            lockHudWidth(loadingDotsLabel);
        }
        if (loadingHintLabel != null) {
            loadingHintLabel.setText("Please wait...");
            lockHudWidth(loadingHintLabel);
        }
    }
    
    private void checkTimerStatus() {
        if (!gameLogicReady) {
            long now = System.currentTimeMillis();
            if (now - lastReconnectAttemptMs >= RECONNECT_INTERVAL_MS) {
                lastReconnectAttemptMs = now;
                initializeGameLogic();
            }
            return;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastTimerCheckMs < TIMER_CHECK_INTERVAL_MS) {
            return;
        }
        lastTimerCheckMs = now;
        
        try {
            pythonClient.connect(2000);
            String response = pythonClient.sendRequest("game_state", "{}");
            JsonValue json = jsonReader.parse(response);
            if (json != null && json.getBoolean("ok", false)) {
                JsonValue data = json.get("data");
                if (data != null) {
                    // Update time remaining
                    float newTimeRemaining = data.getFloat("time_remaining", 15.0f);
                    if (newTimeRemaining > timeRemaining + 5.0f) {
                        // New round started (timer reset to 15)
                        JsonValue seq = data.get("sequence");
                        if (seq != null) {
                            gameSequenceText = "Sequence: " + formatSequence(seq);
                            updateSequenceIcons(seq);
                        }
                    }
                    timeRemaining = newTimeRemaining;
                    
                    // Update expected gesture
                    JsonValue expected = data.get("expected");
                    if (expected != null && expected.isNumber()) {
                        int expectedId = expected.asInt();
                        expectedGestureName = getGestureName(expectedId);
                        gameExpectedText = "Expected: " + (expectedGestureName != null ? getGestureDisplayName(expectedId) : String.valueOf(expectedId));
                    }
                    
                    // Update progress
                    JsonValue progress = data.get("progress");
                    if (progress != null && progress.isArray() && progress.size >= 2) {
                        gameProgressText = "Progress: " + progress.get(0).asString() + "/" + progress.get(1).asString();
                    }
                }
            }
        } catch (Exception ignored) {
            gameLogicReady = false;
            gameStatusMessage = buildBackendStatusMessage();
        }
    }

    private void startFrameThread() {
        running = true;
        frameThread = new Thread(() -> {
            boolean backendReady = false;
            while (running) {
                try {
                    if (!backendReady) {
                        PythonBackendManager.startServer();
                        backendReady = PythonBackendManager.waitForReady(4000);
                        if (!backendReady) {
                            Thread.sleep(250);
                            continue;
                        }
                    }
                    pythonClient.connect(2000);
                    String response = pythonClient.sendRequest("get_frame", "{\"camera_id\":0,\"quality\":60,\"draw_skeleton\":true}");
                    JsonValue json = jsonReader.parse(response);
                    if (json != null && json.getBoolean("ok", false)) {
                        JsonValue data = json.get("data");
                        if (data != null) {
                            String b64 = data.getString("image_b64", null);
                            if (b64 != null && !b64.isEmpty()) {
                                latestFrameBase64 = b64;
                            }
                            handleGestureFromFrame(data);
                        }
                    }
                    Thread.sleep(16);
                } catch (Exception ignored) {
                    backendReady = false;
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "PythonCameraThread");
        frameThread.setDaemon(true);
        frameThread.start();
    }

    private void handleGestureFromFrame(JsonValue data) {
        if (!gameLogicReady) {
            return;
        }
        JsonValue gesture = data.get("gesture");
        if (gesture == null) {
            return;
        }
        boolean isValid = gesture.getBoolean("is_valid", false);
        int gestureId = gesture.getInt("gesture_id", -1);
        if (!isValid || gestureId < 1) {
            return;
        }

        long now = System.currentTimeMillis();
        
        // Check for cooldown after correct gesture (breathing time)
        if (lastCorrectGestureTimeMs > 0 && (now - lastCorrectGestureTimeMs) < CORRECT_GESTURE_COOLDOWN_MS) {
            return;  // Still in cooldown period, ignore all gestures
        }
        
        if (gestureId == lastGestureId && (now - lastGestureTimeMs) < 700) {
            return;
        }

        lastGestureId = gestureId;
        lastGestureTimeMs = now;

        try {
            String payload = "{\"gesture_id\":" + gestureId + "}";
            String response = pythonClient.sendRequest("game_input", payload);
            JsonValue json = jsonReader.parse(response);
            if (json != null && json.getBoolean("ok", false)) {
                JsonValue gameData = json.get("data");
                if (gameData != null) {
                    JsonValue result = gameData.get("result");
                    JsonValue stats = gameData.get("stats");
                    JsonValue expected = gameData.get("expected");
                    JsonValue progress = gameData.get("progress");
                    
                    // Update time remaining
                    if (gameData.has("time_remaining")) {
                        timeRemaining = gameData.getFloat("time_remaining", 15.0f);
                    }
                    
                    // Check for new sequence (round completed or timer expired)
                    if (gameData.has("sequence")) {
                        JsonValue newSeq = gameData.get("sequence");
                        gameSequenceText = "Sequence: " + formatSequence(newSeq);
                        updateSequenceIcons(newSeq);
                        // Reset round start time when new sequence begins
                        roundStartTime = System.currentTimeMillis();
                    }
                    
                    // Get correct status from result object
                    boolean isCorrect = false;
                    boolean isComplete = false;
                    boolean isMistake = false;
                    boolean timeExpired = false;
                    if (result != null) {
                        isCorrect = result.getBoolean("valid", false);
                        isComplete = result.getBoolean("complete", false);
                        isMistake = result.getBoolean("mistake", false);
                        timeExpired = result.getBoolean("time_expired", false);
                    }
                    
                    // Update expected gesture
                    if (expected != null && expected.isNumber()) {
                        int expectedId = expected.asInt();
                        expectedGestureName = getGestureName(expectedId);
                        gameExpectedText = "Expected: " + (expectedGestureName != null ? getGestureDisplayName(expectedId) : String.valueOf(expectedId));
                    }
                    
                    // Update progress
                    if (progress != null && progress.isArray() && progress.size >= 2) {
                        gameProgressText = "Progress: " + progress.get(0).asString() + "/" + progress.get(1).asString();
                    }
                    
                    // Handle time expired
                    if (timeExpired) {
                        gameStatusMessage = "Time's Up! Next round starting...";
                        consecutiveCorrect = 0;
                        comboMultiplier = 1.0f;
                        // Allow immediate input for new round
                        lastCorrectGestureTimeMs = 0L;
                        return;
                    }
                    
                    // Handle mistake - allow mistakes with no penalty
                    if (isMistake) {
                        return;
                    }
                    
                    // Update scoring for correct gestures
                    totalGestures++;
                    if (isCorrect) {
                        correctGestures++;
                        consecutiveCorrect++;
                        
                        // Set cooldown after correct gesture
                        lastCorrectGestureTimeMs = System.currentTimeMillis();
                        
                        // Calculate combo multiplier
                        if (consecutiveCorrect >= 10) {
                            comboMultiplier = 3.0f;
                        } else if (consecutiveCorrect >= 5) {
                            comboMultiplier = 2.0f;
                        } else if (consecutiveCorrect >= 3) {
                            comboMultiplier = 1.5f;
                        } else {
                            comboMultiplier = 1.0f;
                        }
                        
                        // Base points
                        int points = (int)(100 * comboMultiplier);
                        
                        // Speed bonus (if responded quickly)
                        long timeSinceStart = System.currentTimeMillis() - roundStartTime;
                        if (timeSinceStart < 2000 && timeSinceStart > 0) {
                            points += 50;
                        }
                        
                        score += points;
                        gameStatusMessage = "Correct! +" + points + " points";
                        
                        // Check for round completion
                        if (isComplete) {
                            gameStatusMessage = "Round Complete! +" + points + " points - Next round!";
                            // Reset cooldown for new round
                            lastCorrectGestureTimeMs = 0L;
                        }
                    }
                    
                    roundStartTime = System.currentTimeMillis();
                    
                    if (stats != null) {
                        // Check for level completion
                        boolean gameOver = stats.getBoolean("game_over", false);
                        boolean won = stats.getBoolean("won", false);
                        if (gameOver && won) {
                            score += 500;
                            gameStatusMessage = "Game Complete! +500 bonus - You Won!";
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            gameLogicReady = false;
            gameStatusMessage = buildBackendStatusMessage();
        }
    }

    private void initializeGameLogic() {
        try {
            boolean ready = PythonBackendManager.waitForReady(8000);
            if (!ready) {
                gameLogicReady = false;
                gameStatusMessage = buildBackendStatusMessage();
                return;
            }
            pythonClient.connect(2000);
            String response = pythonClient.sendRequest("game_start", "{\"difficulty\":\"medium\"}");
            JsonValue json = jsonReader.parse(response);
            if (json != null && json.getBoolean("ok", false)) {
                gameLogicReady = true;
                gameStatusMessage = "Python game logic ready";
                JsonValue data = json.get("data");
                if (data != null) {
                    // Get initial time remaining
                    if (data.has("time_remaining")) {
                        timeRemaining = data.getFloat("time_remaining", 15.0f);
                    }
                    
                    JsonValue seq = data.get("sequence");
                    gameSequenceText = "Sequence: " + formatSequence(seq);
                    updateSequenceIcons(seq);
                    JsonValue expected = data.get("expected");
                    if (expected != null && expected.isNumber()) {
                        int expectedId = expected.asInt();
                        expectedGestureName = getGestureName(expectedId);
                        gameExpectedText = "Expected: " + (expectedGestureName != null ? getGestureDisplayName(expectedId) : String.valueOf(expectedId));
                    } else {
                        expectedGestureName = null;
                        gameExpectedText = "Expected: -";
                    }
                    JsonValue progress = data.get("progress");
                    if (progress != null && progress.isArray() && progress.size >= 2) {
                        gameProgressText = "Progress: " + progress.get(0).asString() + "/" + progress.get(1).asString();
                    } else {
                        gameProgressText = "Progress: -";
                    }
                }
            } else {
                gameLogicReady = false;
                String error = json != null ? json.getString("error", "unknown_error") : "no_response";
                gameStatusMessage = "Python game logic error: " + error;
            }
        } catch (Exception e) {
            gameLogicReady = false;
            gameStatusMessage = buildBackendStatusMessage();
        }
    }

    private String buildBackendStatusMessage() {
        String error = PythonBackendManager.getLastStartError();
        if (error != null && !error.isEmpty()) {
            return "Python backend error: " + error;
        }
        String logLine = PythonBackendManager.getLastLogLine();
        if (logLine != null && !logLine.isEmpty()) {
            return "Python backend: " + logLine;
        }
        return "Python game logic not ready";
    }

    private String formatSequence(JsonValue seq) {
        if (seq == null || !seq.isArray() || seq.size == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < seq.size; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            // Convert gesture ID to display name
            if (seq.get(i).isNumber()) {
                int gestureId = seq.get(i).asInt();
                builder.append(getGestureDisplayName(gestureId));
            } else {
                builder.append(seq.get(i).asString());
            }
        }
        return builder.toString();
    }

    private void updateSequenceIcons(JsonValue seq) {
        if (sequenceIconsTable == null) {
            return;
        }
        sequenceIconsTable.clearChildren();
        if (seq == null || !seq.isArray() || seq.size == 0) {
            return;
        }
        for (int i = 0; i < seq.size; i++) {
            if (!seq.get(i).isNumber()) {
                continue;
            }
            int gestureId = seq.get(i).asInt();
            String name = getGestureName(gestureId);
            if (name == null || !gestureTextures.containsKey(name)) {
                continue;
            }
            Texture texture = gestureTextures.get(name);
            com.badlogic.gdx.scenes.scene2d.ui.Image icon = new com.badlogic.gdx.scenes.scene2d.ui.Image(texture);
            sequenceIconsTable.add(icon)
                .size(SEQUENCE_ICON_SIZE, SEQUENCE_ICON_SIZE)
                .padLeft(SEQUENCE_ICON_PAD)
                .padRight(SEQUENCE_ICON_PAD);
        }
        sequenceIconsTable.invalidateHierarchy();
    }

    private void stopFrameThread() {
        running = false;
        if (frameThread != null) {
            try {
                frameThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateCameraTexture() {
        String currentBase64 = latestFrameBase64;
        if (currentBase64 == null || currentBase64.isEmpty() || currentBase64.equals(processedFrameBase64)) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(currentBase64);
            Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
            if (cameraTexture != null) {
                cameraTexture.dispose();
            }
            cameraTexture = new Texture(pixmap);
            pixmap.dispose();
            processedFrameBase64 = currentBase64;
            
            // Mark camera as ready once first frame is received
            if (!cameraReady) {
                cameraReady = true;
                loadingMessage = "Camera ready!";
            }
        } catch (Exception ignored) {
        }
    }
}
