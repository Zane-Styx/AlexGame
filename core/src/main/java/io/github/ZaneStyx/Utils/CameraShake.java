package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;

/**
 * Adds a camera shake effect by producing small random offsets.
 */
public class CameraShake {
    private float intensity;
    private float duration;
    private float time;

    public void start(float intensity, float duration) {
        this.intensity = intensity;
        this.duration = duration;
        this.time = 0;
    }

    public boolean isShaking() {
        return time < duration;
    }

    /**
     * Returns a small random offset for the camera position each frame.
     */
    public Vector3 update(float delta) {
        time += delta;
        if (time > duration) return Vector3.Zero;

        float currentIntensity = intensity * (1f - (time / duration));
        float offsetX = (MathUtils.random() - 0.5f) * 2f * currentIntensity;
        float offsetY = (MathUtils.random() - 0.5f) * 2f * currentIntensity;

        return new Vector3(offsetX, offsetY, 0);
    }
}
