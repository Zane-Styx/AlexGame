package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Smooth camera with dead zone, look-ahead, shake, zoom transitions, and cinematic lock mode.
 */
public class CameraController {
    private final OrthographicCamera camera;
    private final Vector2 target = new Vector2();
    private final CameraShake shake = new CameraShake();
    private final ShapeRenderer debugRenderer = new ShapeRenderer();

    private CameraBounds bounds;

    // Smooth follow
    private float smoothSpeed = 5f;

    // Dead zone
    private float deadZoneWidth = 100f;
    private float deadZoneHeight = 60f;
    private boolean showDebugZone = false;

    // Look-ahead
    private boolean enableLookAhead = true;
    private float lookAheadDistance = 50f;
    private float lookAheadSmooth = 3f;
    private final Vector2 lookAheadOffset = new Vector2();
    private final Vector2 prevTargetPos = new Vector2();

    // Zoom
    @SuppressWarnings("unused")private float zoom = 1f;
    private float targetZoom = 1f;
    private float zoomSmooth = 2f;

    // Modes
    public enum FollowTargetMode {
        NONE,       // don't move
        PLAYER,     // follow player
        POINT,      // follow set point
        LOCKED      // cinematic lock — stays fixed
    }

    private FollowTargetMode followMode = FollowTargetMode.NONE;

    // Locked camera position
    private final Vector3 lockedPosition = new Vector3();

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
    }

    // ---------------- CONFIG ----------------
    public void setFollowMode(FollowTargetMode mode) {
        this.followMode = mode;
    }

    public void setTarget(Vector2 target) {
        this.target.set(target);
    }

    public void setBounds(CameraBounds bounds) {
        this.bounds = bounds;
    }

    public void setDeadZone(float width, float height) {
        this.deadZoneWidth = width;
        this.deadZoneHeight = height;
    }

    public void setSmoothSpeed(float speed) {
        this.smoothSpeed = speed;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
        this.targetZoom = zoom;
        camera.zoom = zoom;
    }

    public void setTargetZoom(float zoom, float smoothTime) {
        this.targetZoom = zoom;
        this.zoomSmooth = smoothTime;
    }

    public void setDebugZoneVisible(boolean visible) {
        this.showDebugZone = visible;
    }

    public void setLookAheadEnabled(boolean enabled) {
        this.enableLookAhead = enabled;
    }

    public void setLookAheadDistance(float distance) {
        this.lookAheadDistance = distance;
    }

    public void setLookAheadSmooth(float smooth) {
        this.lookAheadSmooth = smooth;
    }

    public void lockCamera(Vector3 position) {
        lockedPosition.set(position);
        followMode = FollowTargetMode.LOCKED;
    }

    public void unlockCamera() {
        followMode = FollowTargetMode.PLAYER;
    }

    // ---------------- UPDATE ----------------
    public void update(float delta) {
        // Smooth zoom interpolation
        camera.zoom += (targetZoom - camera.zoom) * zoomSmooth * delta;

        if (followMode == FollowTargetMode.NONE) {
            camera.update();
            return;
        }

        if (followMode == FollowTargetMode.LOCKED) {
            camera.position.lerp(lockedPosition, 0.1f);
            camera.update();
            return;
        }

        // Look-ahead
        if (enableLookAhead) {
            float dx = target.x - prevTargetPos.x;
            float desiredOffsetX = Math.signum(dx) * lookAheadDistance;
            lookAheadOffset.x += (desiredOffsetX - lookAheadOffset.x) * lookAheadSmooth * delta;
            prevTargetPos.set(target);
        } else {
            lookAheadOffset.setZero();
        }

        float targetX = target.x + lookAheadOffset.x;
        float targetY = target.y;

        // Dead zone logic
        float dxCam = targetX - camera.position.x;
        float dyCam = targetY - camera.position.y;

        if (Math.abs(dxCam) > deadZoneWidth / 2f || Math.abs(dyCam) > deadZoneHeight / 2f) {
            camera.position.x += dxCam * smoothSpeed * delta;
            camera.position.y += dyCam * smoothSpeed * delta;
        }

        // Bounds
        if (bounds != null) {
            Vector3 clamped = bounds.clamp(camera.position, camera.viewportWidth * camera.zoom, camera.viewportHeight * camera.zoom);
            camera.position.set(clamped);
        }

        // Shake
        if (shake.isShaking()) {
            Vector3 offset = shake.update(delta);
            camera.position.add(offset);
        }

        camera.update();

        if (showDebugZone) renderDebugZone();
    }

    // ---------------- EFFECTS ----------------
    public void shake(float intensity, float duration) {
        shake.start(intensity, duration);
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    // ---------------- DEBUG ----------------
    private void renderDebugZone() {
        debugRenderer.setProjectionMatrix(camera.combined);
        debugRenderer.begin(ShapeRenderer.ShapeType.Line);
        debugRenderer.setColor(Color.YELLOW);

        float left = camera.position.x - deadZoneWidth / 2f;
        float bottom = camera.position.y - deadZoneHeight / 2f;
        debugRenderer.rect(left, bottom, deadZoneWidth, deadZoneHeight);

        debugRenderer.end();
    }

    public void dispose() {
        debugRenderer.dispose();
    }
}
