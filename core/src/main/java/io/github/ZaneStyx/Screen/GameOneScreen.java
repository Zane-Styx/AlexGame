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
import com.badlogic.gdx.scenes.scene2d.ui.Image;
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
    private Texture cameraTexture;
    private Texture gameUiTexture;
    private Image gameUiImage;
    private Table uiPanel;
    private final JsonReader jsonReader = new JsonReader();
    private static final float UI_PANEL_WIDTH = 512f;
    private static final float UI_PANEL_HEIGHT = 768f;
    private static final float UI_PANEL_LEFT_PADDING = 768f;
    private static final float EXIT_BUTTON_WIDTH = 186f;
    private static final float EXIT_BUTTON_HEIGHT = 56f;
    private volatile boolean gameLogicReady;
    private volatile String gameStatusMessage = "Starting Python game logic...";
    private volatile String gameSequenceText = "";
    private volatile String gameExpectedText = "";
    private volatile String gameProgressText = "";
    private volatile int lastGestureId = -1;
    private volatile long lastGestureTimeMs = 0L;

    @Override
    public void show() {
        batch = new SpriteBatch();
        font = new BitmapFont();

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = Assets.manager.get("ui/uiskin.json", Skin.class);

        gameUiTexture = Assets.manager.get("ui/game/game_one_ui.png", Texture.class);
        gameUiImage = new Image(gameUiTexture);
        stage.addActor(gameUiImage);

        uiPanel = new Table();
        uiPanel.setSize(UI_PANEL_WIDTH, UI_PANEL_HEIGHT);
        uiPanel.setPosition(UI_PANEL_LEFT_PADDING, 0f);
        uiPanel.bottom().center();
        ImageButton backButton = createBackButton();
        uiPanel.add(backButton).width(EXIT_BUTTON_WIDTH).height(EXIT_BUTTON_HEIGHT).padBottom(24f);
        stage.addActor(uiPanel);

        updateGameUiLayout();

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
        updateGameUiLayout();

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
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
        updateGameUiLayout();
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

    private void startFrameThread() {
        running = true;
        frameThread = new Thread(() -> {
            while (running) {
                try {
                    pythonClient.connect(2000);
                    String response = pythonClient.sendRequest("get_frame", "{\"camera_id\":0,\"quality\":70,\"draw_skeleton\":true}");
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
                    Thread.sleep(33);
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
                    JsonValue stats = gameData.get("stats");
                    JsonValue expected = gameData.get("expected");
                    JsonValue progress = gameData.get("progress");
                    if (expected != null) {
                        gameExpectedText = "Expected: " + expected.asString();
                    }
                    if (progress != null && progress.isArray() && progress.size >= 2) {
                        gameProgressText = "Progress: " + progress.get(0).asString() + "/" + progress.get(1).asString();
                    }
                    if (stats != null) {
                        int score = stats.getInt("score", 0);
                        gameStatusMessage = "Score: " + score;
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
                    JsonValue seq = data.get("sequence");
                    gameSequenceText = "Sequence: " + formatSequence(seq);
                    JsonValue expected = data.get("expected");
                    gameExpectedText = expected == null ? "Expected: -" : "Expected: " + expected.asString();
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
            builder.append(seq.get(i).asString());
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
        if (latestFrameBase64 == null || latestFrameBase64.isEmpty()) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(latestFrameBase64);
            Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
            if (cameraTexture == null) {
                cameraTexture = new Texture(pixmap);
            } else {
                cameraTexture.draw(pixmap, 0, 0);
            }
            pixmap.dispose();
        } catch (Exception ignored) {
        }
    }

    private void updateGameUiLayout() {
        if (gameUiImage == null) {
            return;
        }
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        gameUiImage.setSize(width, height);
        gameUiImage.setPosition(0f, 0f);
        gameUiImage.setZIndex(0);

        if (uiPanel != null) {
            float panelY = Math.max(0f, (height - UI_PANEL_HEIGHT) / 2f);
            uiPanel.setSize(UI_PANEL_WIDTH, UI_PANEL_HEIGHT);
            uiPanel.setPosition(UI_PANEL_LEFT_PADDING, panelY);
        }
    }
}
