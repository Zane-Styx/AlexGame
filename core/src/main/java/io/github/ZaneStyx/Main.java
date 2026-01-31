package io.github.ZaneStyx;

import com.badlogic.gdx.Game;
import io.github.ZaneStyx.Screen.*;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {
    
    @Override
    public void create() {
            // Normal LibGDX game flow
            setScreen(new LoadingScreen(this, new MainMenuScreen(this)));
        }
    }
