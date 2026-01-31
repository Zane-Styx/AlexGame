package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Timer;

import java.util.*;

/**
 * SoundManager - full-featured audio management for LibGDX.
 *
 * Features:
 *  • Sound & music management with playlists and shuffle.
 *  • Smooth crossfade between tracks.
 *  • Separate volume controls (Master / Music / SFX).
 *  • Smooth fade transitions for volume controls.
 *  • Optional cutoff for overlapping sounds.
 *
 * Example:
 * SoundManager.init(true);
 * SoundManager.addMusic("theme", "music/track1.ogg", false);
 * SoundManager.addMusic("theme", "music/track2.ogg", false);
 * SoundManager.playPlaylist("theme", 2f, true);
 * SoundManager.fadeMusicVolume(0.2f, 2f); // fade to 20% over 2 seconds
 */
public class SoundManager {

    private static final Map<String, Sound> sounds = new HashMap<>();
    private static final Map<String, List<Music>> musicGroups = new HashMap<>();
    private static final Map<String, Queue<Music>> shuffleQueues = new HashMap<>();
    private static final Map<String, Long> soundInstances = new HashMap<>();
    private static final Map<String, Music> currentTracks = new HashMap<>();
    private static final Map<String, Music> loopingSfx = new HashMap<>();

    private static boolean cutoff = false;
    private static float masterVolume = 1f;
    private static float musicVolume = 1f;
    private static float sfxVolume = 1f;

    private static final Random random = new Random();

    private SoundManager() {}

    // ---------------- Initialization ----------------

    public static void init(boolean cutoffEnabled) {
        cutoff = cutoffEnabled;
    }

    // ---------------- Audio Loading ----------------

    public static void addSound(String name, String path) {
        sounds.put(name, Gdx.audio.newSound(Gdx.files.internal(path)));
    }

    public static void addMusic(String group, String path, boolean loop) {
        Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
        music.setLooping(loop);
        musicGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(music);
    }

    public static void addLoopingSfx(String name, String path) {
        Music m = Gdx.audio.newMusic(Gdx.files.internal(path));
        m.setLooping(true); // plays full file before looping
        m.setVolume(masterVolume * sfxVolume);
        loopingSfx.put(name, m);
    }


    // ---------------- Playback ----------------

    public static void play(String name) {
        if (sounds.containsKey(name)) {
            // ✅ Stop all currently playing SFX (but not music) if cutoff is enabled
            if (cutoff) stopAllSounds();

            playSound(name);
        } else if (musicGroups.containsKey(name)) {
            playMusic(name, 0f, false);
        } else {
            Gdx.app.log("SoundManager", "No audio found: " + name);
        }
    }

    public static void playLoopingSfx(String name) {
        Music m = loopingSfx.get(name);
        if (m != null) {
            m.setVolume(masterVolume * sfxVolume);
            m.play();
        }
    }

    public static void stopLoopingSfx(String name) {
        Music m = loopingSfx.get(name);
        if (m != null) {
            m.stop();
        }
    }

    private static void playSound(String name) {
        Sound s = sounds.get(name);
        if (cutoff && soundInstances.containsKey(name)) {
            s.stop(soundInstances.get(name));
        }
        long id = s.play(masterVolume * sfxVolume);
        soundInstances.put(name, id);
    }

    public static void playMusic(String group, float crossfadeDuration, boolean shuffle) {
        List<Music> groupList = musicGroups.get(group);
        if (groupList == null || groupList.isEmpty()) return;

        Music next;
        if (shuffle) {
            Queue<Music> queue = shuffleQueues.computeIfAbsent(group, k -> new LinkedList<>());
            if (queue.isEmpty()) {
                List<Music> shuffled = new ArrayList<>(groupList);
                Collections.shuffle(shuffled);
                queue.addAll(shuffled);
            }
            next = queue.poll();
        } else {
            next = groupList.get(random.nextInt(groupList.size()));
        }

        Music current = currentTracks.get(group);
        if (current == next) return;

        if (cutoff && current != null && current.isPlaying()) {
            fadeOut(current, crossfadeDuration / 2f);
        }

        next.setVolume(0f);
        next.play();
        currentTracks.put(group, next);
        fadeIn(next, crossfadeDuration / 2f);

        next.setOnCompletionListener(m -> Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                playMusic(group, crossfadeDuration, shuffle);
            }
        }, 0.25f));
    }

    public static void playPlaylist(String group, float crossfadeDuration, boolean shuffle) {
        playMusic(group, crossfadeDuration, shuffle);
    }

    // ---------------- Fading ----------------

    private static void fadeIn(final Music music, final float duration) {
        if (duration <= 0f) {
            music.setVolume(masterVolume * musicVolume);
            return;
        }
        final float interval = 0.05f;
        final int steps = (int) (duration / interval);
        Timer.schedule(new Timer.Task() {
            float progress = 0f;
            @Override
            public void run() {
                progress += 1f / steps;
                float volume = Math.min(progress, 1f) * masterVolume * musicVolume;
                music.setVolume(volume);
                if (progress >= 1f) cancel();
            }
        }, 0f, interval);
    }

    private static void fadeOut(final Music music, final float duration) {
        if (duration <= 0f) {
            music.stop();
            return;
        }
        final float interval = 0.05f;
        final int steps = (int) (duration / interval);
        Timer.schedule(new Timer.Task() {
            float progress = 0f;
            @Override
            public void run() {
                progress += 1f / steps;
                float volume = (1f - progress) * masterVolume * musicVolume;
                music.setVolume(Math.max(volume, 0f));
                if (progress >= 1f) {
                    music.stop();
                    cancel();
                }
            }
        }, 0f, interval);
    }

    // ---------------- Volume Controls ----------------

    public static void setMasterVolume(float volume) {
        masterVolume = clamp(volume);
        updateMusicVolumes();
    }

    public static void setMusicVolume(float volume) {
        musicVolume = clamp(volume);
        updateMusicVolumes();
    }

    public static void setSfxVolume(float volume) {
        sfxVolume = clamp(volume);
    }

    // --- NEW: Smooth Fades for Volume Changes ---

    /** Smoothly fades master volume to target over duration (seconds). */
    public static void fadeMasterVolume(float target, float duration) {
        fadeVolume(() -> masterVolume, v -> { masterVolume = v; updateMusicVolumes(); }, target, duration);
    }

    /** Smoothly fades music volume to target over duration (seconds). */
    public static void fadeMusicVolume(float target, float duration) {
        fadeVolume(() -> musicVolume, v -> { musicVolume = v; updateMusicVolumes(); }, target, duration);
    }

    /** Smoothly fades SFX volume to target over duration (seconds). */
    public static void fadeSfxVolume(float target, float duration) {
        fadeVolume(() -> sfxVolume, v -> sfxVolume = v, target, duration);
    }

    private static void fadeVolume(SupplierFloat getter, ConsumerFloat setter, float target, float duration) {
        final float targetVolume = clamp(target); // ✅ make it final for inner class use
        if (duration <= 0f) {
            setter.accept(targetVolume);
            return;
        }

        final float start = getter.get();
        final float interval = 0.05f;
        final int steps = (int) (duration / interval);

        Timer.schedule(new Timer.Task() {
            int count = 0;
            @Override
            public void run() {
                count++;
                float t = (float) count / steps;
                float value = start + (targetVolume - start) * t;
                setter.accept(value);
                if (count >= steps) cancel();
            }
        }, 0f, interval);
    }

    private static void updateMusicVolumes() {
        for (Music m : currentTracks.values()) {
            m.setVolume(masterVolume * musicVolume);
        }
        for (Music m : loopingSfx.values()) {
            m.setVolume(masterVolume * sfxVolume);
        }
    }

    // ---------------- Stop / Cleanup ----------------

    public static void stop(String name) {
        if (sounds.containsKey(name)) {
            if (soundInstances.containsKey(name)) {
                sounds.get(name).stop(soundInstances.get(name));
                soundInstances.remove(name);
            }
        } else if (musicGroups.containsKey(name)) {
            for (Music m : musicGroups.get(name)) {
                m.stop();
            }
            currentTracks.remove(name);
        }
    }

    /** Stops all currently playing sound effects (not music). */
    private static void stopAllSounds() {
        for (Map.Entry<String, Sound> e : sounds.entrySet()) {
            if (soundInstances.containsKey(e.getKey())) {
                e.getValue().stop(soundInstances.get(e.getKey()));
            }
        }
        soundInstances.clear();
    }

    public static void stopAll() {
        for (Map.Entry<String, Sound> e : sounds.entrySet()) {
            if (soundInstances.containsKey(e.getKey())) {
                e.getValue().stop(soundInstances.get(e.getKey()));
            }
        }
        for (List<Music> group : musicGroups.values()) {
            for (Music m : group) m.stop();
        }
        for (Music m : loopingSfx.values()) m.stop();

        soundInstances.clear();
        currentTracks.clear();
    }

    public static void dispose() {
        stopAll();
        for (Sound s : sounds.values()) s.dispose();
        for (List<Music> list : musicGroups.values()) {
            for (Music m : list) m.dispose();
        }
        for (Music m : loopingSfx.values()) m.dispose();
        loopingSfx.clear();
        sounds.clear();
        musicGroups.clear();
        shuffleQueues.clear();
        soundInstances.clear();
        currentTracks.clear();
    }

    // ---------------- Helpers ----------------

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private interface SupplierFloat {
        float get();
    }

    private interface ConsumerFloat {
        void accept(float v);
    }
}
