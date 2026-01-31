package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.math.Vector3;

/**
 * Restricts camera movement within a world region.
 */
public class CameraBounds {
    private float minX, maxX, minY, maxY;

    public CameraBounds(float minX, float maxX, float minY, float maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    public Vector3 clamp(Vector3 pos, float viewW, float viewH) {
        float halfW = viewW / 2f;
        float halfH = viewH / 2f;

        pos.x = Math.max(minX + halfW, Math.min(maxX - halfW, pos.x));
        pos.y = Math.max(minY + halfH, Math.min(maxY - halfH, pos.y));
        return pos;
    }
}
