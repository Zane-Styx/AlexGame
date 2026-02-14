package io.github.ZaneStyx.Screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.ZaneStyx.Utils.Assets;
import io.github.ZaneStyx.Utils.PythonBackendManager;
import io.github.ZaneStyx.Utils.UIHelper;

public class MainMenuScreen implements Screen {
    private OrthographicCamera camera;
    private Stage stage;
    private Skin skin;
    private final Game game;
    private int smallBtnSizeWidth = 42;
    private int smallBtnSizeHeight = 46;
    private int largeBtnSizeWidth = 186;
    private int largeBtnSizeHeight = 56;

    public MainMenuScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        
        // Load UI skin
        skin = Assets.manager.get("ui/uiskin.json", Skin.class);
        
        setupUI();
    }

    private void setupUI() {
        // Root table fills the entire screen
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(20f);
        
        // Title at the top using sprite-based label
        com.badlogic.gdx.scenes.scene2d.Actor titleLabel = UIHelper.createSpriteLabel("TITLE", UIHelper.DEFAULT_SPRITE_FONT, 4.0f);
        rootTable.add(titleLabel).center().padBottom(30f).row();
        
        // Game buttons (1x2)
        Table gameGrid = new Table();

        com.badlogic.gdx.scenes.scene2d.Actor game1 = UIHelper.createButton("Game One", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (game != null) {
                    game.setScreen(new GameOneScreen());
                }
            }
        });

        com.badlogic.gdx.scenes.scene2d.Actor game2 = UIHelper.createButton("Game Two", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (game != null) {
                    game.setScreen(new GameTwoScreen());
                }
            }
        });

        gameGrid.add(game1).width(180f).height(70f).pad(10f);
        gameGrid.add(game2).width(180f).height(70f).pad(10f);

        rootTable.add(gameGrid).center().padBottom(30f).row();

        // Settings + Credits row above Exit
        Table metaRow = new Table();
        ImageButton settingsButton = createSettingsButton();
        ImageButton creditsButton = createCreditsButton();
        metaRow.add(settingsButton).width(smallBtnSizeWidth).height(smallBtnSizeHeight).pad(10f);
        metaRow.add(creditsButton).width(smallBtnSizeWidth).height(smallBtnSizeHeight).pad(10f);
        rootTable.add(metaRow).center().padBottom(20f).row();

        // Exit button at the bottom (image button)
        ImageButton exitButton = createExitButton();
        rootTable.add(exitButton).width(largeBtnSizeWidth).height(largeBtnSizeHeight);
        
        stage.addActor(rootTable);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.07f, 0.07f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
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
        // Clear input processor when leaving this screen
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        try { if (stage != null) stage.dispose(); } catch (Exception ignored) {}
    }

    private ImageButton createSettingsButton() {
        return UIHelper.createImageButton(
            "ui/settingsBtn/SettingsBtn_0.png",
            "ui/settingsBtn/SettingsBtn_0.png",
            "ui/settingsBtn/SettingsBtn_1.png",
            "ui/settingsBtn/SettingsBtn_Hover.png",
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new SettingsScreen());
                }
            }
        );
    }

    private ImageButton createCreditsButton() {
        return UIHelper.createImageButton(
            "ui/creditsBtn/CreditsBtn_0.png",
            "ui/creditsBtn/CreditsBtn_0.png",
            "ui/creditsBtn/CreditsBtn_1.png",
            "ui/creditsBtn/CreditsBtn_Hover.png",
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new CreditsScreen());
                }
            }
        );
    }

    private ImageButton createExitButton() {
        return UIHelper.createImageButton(
            "ui/exitBtn/ExitBtn_0.png",
            "ui/exitBtn/ExitBtn_0.png",
            "ui/exitBtn/ExitBtn_1.png",
            "ui/exitBtn/ExitBtn_Hover.png",
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    PythonBackendManager.stopServer();
                    Gdx.app.exit();
                }
            }
        );
    }
}
