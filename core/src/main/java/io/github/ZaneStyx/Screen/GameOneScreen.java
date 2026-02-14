package io.github.ZaneStyx.Screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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
import io.github.ZaneStyx.Utils.UIHelper;

import java.util.Base64;

public class GameOneScreen implements Screen {
    private SpriteBatch batch;
    private BitmapFont font;
    private Stage stage;
    private Skin skin;
    private PythonBridgeClient pythonClient;
    private Thread frameThread;
    private volatile boolean running;
    private volatile String latestFrameBase64;
    private String processedFrameBase64;
    private Texture cameraTexture;
    private Table uiPanel;
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
    private volatile float timeRemaining = 10.0f;
    private volatile long lastTimerCheckMs = 0L;
    private static final long TIMER_CHECK_INTERVAL_MS = 100L;  // Check timer every 100ms
    private volatile long lastCorrectGestureTimeMs = 0L;
    private static final long CORRECT_GESTURE_COOLDOWN_MS = 1500L;  // Breathing time after correct gesture

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
        font = new BitmapFont();

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = Assets.manager.get("ui/uiskin.json", Skin.class);

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
        uiPanel.top().left().pad(20f);
        ImageButton backButton = createBackButton();
        uiPanel.add(backButton).width(EXIT_BUTTON_WIDTH).height(EXIT_BUTTON_HEIGHT);
        stage.addActor(uiPanel);

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

        // Show loading screen while camera initializes
        if (!cameraReady) {
            batch.begin();
            font.getData().setScale(2.0f);
            float textX = Gdx.graphics.getWidth() / 2f - 150f;
            float textY = Gdx.graphics.getHeight() / 2f;
            font.draw(batch, loadingMessage, textX, textY);
            
            // Show dots animation
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
            font.draw(batch, "Waiting for Python camera feed...", 20, Gdx.graphics.getHeight() - 20);
        }
        // Draw scoring UI
        font.getData().setScale(2.0f);
        font.draw(batch, "Score: " + score, 20, Gdx.graphics.getHeight() - 80);
        
        // Draw timer
        font.getData().setScale(2.5f);
        int timeInt = (int)Math.ceil(timeRemaining);
        String timerColor = timeRemaining < 3.0f ? "[RED]" : timeRemaining < 6.0f ? "[YELLOW]" : "[GREEN]";
        font.draw(batch, "Time: " + timeInt + "s", 20, Gdx.graphics.getHeight() - 130);
        
        font.getData().setScale(1.5f);
        if (comboMultiplier > 1.0f) {
            font.draw(batch, "Combo: x" + String.format("%.1f", comboMultiplier), 20, Gdx.graphics.getHeight() - 180);
        }
        if (correctGestures > 0 || totalGestures > 0) {
            font.draw(batch, "Accuracy: " + correctGestures + "/" + totalGestures, 20, Gdx.graphics.getHeight() - 220);
        }
        font.getData().setScale(1.0f);
        if (gameStatusMessage != null && !gameStatusMessage.isEmpty()) {
            font.draw(batch, gameStatusMessage, 20, 40);
        }
        if (gameSequenceText != null && !gameSequenceText.isEmpty()) {
            font.draw(batch, gameSequenceText, 20, 70);
        }
        if (gameExpectedText != null && !gameExpectedText.isEmpty()) {
            font.draw(batch, gameExpectedText, 20, 100);
        }
        if (gameProgressText != null && !gameProgressText.isEmpty()) {
            font.draw(batch, gameProgressText, 20, 130);
        }
        
        // Draw expected gesture image
        if (expectedGestureName != null && gestureTextures.containsKey(expectedGestureName.toLowerCase())) {
            Texture gestureTexture = gestureTextures.get(expectedGestureName.toLowerCase());
            float gestureSize = 256f;
            float gestureX = Gdx.graphics.getWidth() - gestureSize - 50f;
            float gestureY = Gdx.graphics.getHeight() - gestureSize - 50f;
            batch.draw(gestureTexture, gestureX, gestureY, gestureSize, gestureSize);
            
            // Draw label above the gesture
            font.getData().setScale(1.5f);
            font.draw(batch, "Expected:", gestureX, gestureY + gestureSize + 30f);
            font.getData().setScale(1.0f);
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
        try { if (font != null) font.dispose(); } catch (Exception ignored) {}
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
    
    private void checkTimerStatus() {
        if (!gameLogicReady) {
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
                    float newTimeRemaining = data.getFloat("time_remaining", 10.0f);
                    if (newTimeRemaining > timeRemaining + 5.0f) {
                        // New round started (timer reset to 10)
                        JsonValue seq = data.get("sequence");
                        if (seq != null) {
                            gameSequenceText = "Sequence: " + formatSequence(seq);
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
        }
    }

    private void startFrameThread() {
        running = true;
        frameThread = new Thread(() -> {
            while (running) {
                try {
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
                        timeRemaining = gameData.getFloat("time_remaining", 10.0f);
                    }
                    
                    // Check for new sequence (round completed or timer expired)
                    if (gameData.has("sequence")) {
                        JsonValue newSeq = gameData.get("sequence");
                        gameSequenceText = "Sequence: " + formatSequence(newSeq);
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
                        lastCorrectGestureTimeMs = 0L;  // Reset cooldown for new round
                        return;
                    }
                    
                    // Handle mistake - player must restart sequence
                    if (isMistake) {
                        gameStatusMessage = "Wrong gesture! Start over - " + getGestureDisplayName(gestureId) + " was incorrect";
                        consecutiveCorrect = 0;
                        comboMultiplier = 1.0f;
                        score = Math.max(0, score - 50);
                        totalGestures++;
                        lastCorrectGestureTimeMs = 0L;  // Reset cooldown so player can try again immediately
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
                        timeRemaining = data.getFloat("time_remaining", 10.0f);
                    }
                    
                    JsonValue seq = data.get("sequence");
                    gameSequenceText = "Sequence: " + formatSequence(seq);
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
