package io.github.ZaneStyx.Screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.ZaneStyx.Utils.Assets;
import io.github.ZaneStyx.Utils.UIHelper;

public class GameTwoScreen implements Screen {
    private SpriteBatch batch;
    private BitmapFont font;
    private Stage stage;
    private Skin skin;

    @Override
    public void show() {
        batch = new SpriteBatch();
        font = new BitmapFont();

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = Assets.manager.get("ui/uiskin.json", Skin.class);

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(20f);
        ImageButton backButton = createBackButton();
        root.add(backButton).width(210f).height(230f);
        stage.addActor(root);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        font.draw(batch, "Game Two", 20, Gdx.graphics.getHeight() - 20);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
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
    }

    @Override
    public void dispose() {
        try { if (batch != null) batch.dispose(); } catch (Exception ignored) {}
        try { if (font != null) font.dispose(); } catch (Exception ignored) {}
        try { if (stage != null) stage.dispose(); } catch (Exception ignored) {}
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
}
