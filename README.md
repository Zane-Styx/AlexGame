# ZealGame - Hand Gesture Gaming

A [libGDX](https://libgdx.com/) project with MediaPipe hand gesture recognition for interactive gesture-based gameplay.

## Game Mechanics

This game features two unique hand gesture-controlled games that utilize real-time camera tracking and MediaPipe gesture recognition.

### Game One: Gesture Memory Challenge

**Objective:** Memorize and repeat increasingly complex gesture sequences within a time limit.

#### How to Play
1. **Sequence Display:** At the start of each round, 4 random gestures are shown on screen
2. **Memorize:** Study the sequence (e.g., "Peace, OK, HighFive, Fist")
3. **Perform:** Use your hand to perform each gesture in the exact order
4. **Time Limit:** You have 10 seconds to complete the sequence
5. **Retry on Mistakes:** If you perform the wrong gesture, the sequence resets but the timer keeps running
6. **Progress:** Successfully complete the sequence to advance to the next round with a new 4-gesture combination

#### Game Rules
- **Fixed Sequence Length:** Always 4 gestures per round
- **All Gestures Used:** Any of the 7 gestures can appear (Peace, OK, HighFive, Fist, Point, Rock, ILoveYou)
- **10-Second Timer:** Each round has exactly 10 seconds to complete
- **Cooldown Period:** After each correct gesture, there's a 1.5-second cooldown to give you time to change hand positions
- **Mistakes Are Ignored:** Wrong gestures do not reset progress; you can keep trying until time runs out
- **Auto-Advance:** When timer expires OR sequence is completed, a new round starts immediately with a fresh sequence

#### Scoring System
- **Base Points:** 100 points per correct gesture
- **Combo Multiplier:**
  - 3+ consecutive correct: 1.5x multiplier
  - 5+ consecutive correct: 2.0x multiplier
  - 10+ consecutive correct: 3.0x multiplier
- **Speed Bonus:** +50 points if you respond within 2 seconds
- **No Penalty:** Wrong gestures do not affect score
- **Accuracy Tracking:** Stats show correct/total gestures attempted

#### Visual Display
- **Camera Feed:** Live video showing your hand with skeleton overlay
- **Timer:** Large countdown timer (turns yellow at 6s, red at 3s)
- **Expected Gesture:** Current gesture you need to perform shown in top-right corner
- **Sequence:** Full 4-gesture sequence displayed at bottom
- **Progress:** Shows how many gestures completed (e.g., "Progress: 2/4")
- **Score & Combo:** Real-time score and combo multiplier display

---

### Game Two: Bug Ninja

**Objective:** Slice flying bugs with your hand before they escape off screen, avoiding penalties.

#### How to Play
1. **Hand Tracking:** Your hand position is tracked in real-time with a sword following your movements
2. **Bug Spawning:** Colorful bugs fly into the screen from various directions
3. **Slice Bugs:** Move your hand over bugs to slice them
4. **Avoid Misses:** Don't let bugs escape off the edges of the camera area
5. **Build Combos:** Slice multiple bugs quickly to build combo multipliers

#### Game Rules
- **Hand Trail:** Your hand leaves a visual trail showing movement path
- **Collision Detection:** Bugs are sliced when your hand trail intersects them
- **Spawn Rate:** New bugs appear at regular intervals (every 2 seconds)
- **Bug Movement:** Each bug has random velocity and curved trajectory
- **Escape Penalty:** Bugs that leave the camera area without being sliced incur penalties
- **Combo Window:** Slice bugs within 1 second of each other to maintain combo

#### Bug Types & Points
- **Purple Bugs:** 10 points (slowest)
- **Green Bugs:** 20 points
- **Blue Bugs:** 30 points
- **Red Bugs:** 50 points (fastest, most valuable)

#### Scoring System
- **Bug Points:** Base points based on bug color/type
- **Combo Multiplier:**
  - 3+ bugs in combo: 2.0x multiplier
  - 5+ bugs in combo: 3.0x multiplier
- **Miss Penalty:** -10 points when a bug escapes
- **Stats Tracking:** Displays bugs sliced vs. bugs missed

#### Visual Display
- **Camera Feed:** Semi-transparent live video (30% opacity) as background
- **Hand Landmarks:** Optional debug display of 21 hand landmark points
- **Hand Trail:** Smooth trail following hand movement
- **Sword Graphic:** Animated sword aligned with hand movement direction
- **Bugs:** Animated sprites with rotation and smooth movement
- **Performance Optimized:** Runs at 60 FPS with consolidated rendering
- **UI:** Score, combo multiplier, high score, and stats displayed

#### Performance Features
- **60 FPS Target:** Optimized rendering with minimal overhead
- **Smart Rendering:** Consolidated ShapeRenderer passes (2 instead of 3)
- **No Logging:** Performance-critical sections avoid console output
- **Smooth Interpolation:** Hand position uses lerp for fluid movement
- **Camera Buffer:** 100px border around camera feed for gameplay area

---

### Common Features (Both Games)

#### Camera Loading Screen
- Both games display "Initializing camera..." with animated dots
- Game doesn't start until first camera frame is received
- Prevents playing before camera is ready

#### Gesture Recognition
- **Real-time Detection:** Uses MediaPipe for accurate hand tracking
- **7 Gestures Supported:** Peace, OK, HighFive, Fist, Point, Rock, ILoveYou
- **Stability Filtering:** Requires consistent detection to prevent false positives
- **Visual Feedback:** Expected gestures shown as images for easy identification

#### Controls
- **Back Button:** Top-left corner returns to main menu
- **Camera Feed:** Live view with real-time hand skeleton overlay
- **Responsive UI:** All elements scale properly with window size

---

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.

## Gesture Recognition System

This game features an **enhanced MediaPipe-based hand gesture recognition system** for interactive gameplay.

### Key Features

#### 7 Supported Gestures
- **Core gestures** (1, 2, 4): Peace ✌️, OK 👌, High Five 🖐️
- **Extended gestures** (5-10): Fist ✊, Point 👉, Rock 🤘, I Love You 🤟

#### Advanced Performance Optimizations (v2)
- **ROI Tracking**: 30-50% faster by tracking hand in smaller region
- **Motion Detection**: 20-40% CPU savings when hand is stable
- **Kalman Filtering**: Smooth landmark tracking without lag (optional)
- **Vectorized Operations**: 3x faster gesture recognition with NumPy
- **Adaptive FPS Control**: Automatically adjusts quality based on system performance
- **GPU Acceleration**: Utilizes Jetson GPU or CPU fallback
- **Smart Frame Skipping**: Reduces latency without sacrificing accuracy
- **CLAHE Enhancement**: Better detection in varying lighting conditions

#### Accuracy Improvements
- **Temporal Smoothing**: Reduces gesture flickering with 5-frame history buffer
- **Confidence Scoring**: Multi-factor accuracy assessment
- **Stability Detection**: Requires 2 consecutive detections to prevent false positives
- **Advanced Finger Detection**: Angle-based analysis for complex gestures

#### Performance Monitoring
- Real-time FPS tracking
- Detection latency measurement
- Gesture stability scoring
- Frame drop rate monitoring

### Testing Gesture Recognition

Install dependencies first:

```bash
cd python
pip install -r requirements.txt
```

For optimal performance (recommended), install Kalman filter support:

```bash
pip install filterpy
```

Run the test script to visualize gesture detection:

```bash
python test_gestures.py
```

**Controls:**
- `q` - Quit
- `s` - Toggle stats display
- `r` - Reset statistics

### Configuration

#### Hand Tracker Settings
```python
tracker = HandTracker(
    camera_id=0,                  # Webcam index
    flip_horizontal=True,         # Mirror for natural interaction  
    use_gpu=True,                 # Enable GPU acceleration
    adaptive_performance=True,    # Dynamic quality adjustment
    enable_roi_tracking=True,     # Region of interest tracking (+35% FPS)
    enable_kalman_filter=True,    # Smooth landmark tracking (requires filterpy)
    motion_detection=True         # Skip detection when stable (-35% CPU)
)
```

#### Gesture Recognizer Settings
```python
recognizer = GestureRecognizer(
    confidence_threshold=0.6,  # Min confidence (0-1)
    enable_smoothing=True      # Temporal smoothing
)
```

### Performance Expectations

| Platform | FPS | Latency | Resolution Scale | CPU Usage (Idle) |
|----------|-----|---------|------------------|------------------|
| Jetson (GPU) + v2 | 38-40 | 25-35ms | 1.0x (full) | 45-55% |
| Desktop CPU + v2 | 32-35 | 30-40ms | 0.7x (adaptive) | 60-70% |
| Low-end CPU + v2 | 25-28 | 35-50ms | 0.5x (adaptive) | 75-85% |

**v2 Improvements over v1:**
- +30-35% FPS increase
- -30-40% CPU usage reduction (when hand idle)
- -40% detection latency
- -70% landmark jitter with Kalman filtering

### Documentation

See [GESTURE_GUIDE.md](GESTURE_GUIDE.md) for:
- Detailed gesture descriptions
- Detection tips and troubleshooting
- Technical implementation details
- How to add custom gestures

### Python Bridge Server

The gesture recognition runs as a bridge server that communicates with the Java game:

```bash
cd python
python bridge_server.py
```

The server listens on **port 8765** and provides:
- `process_frame`: Send image, get gesture detection
- `get_frame`: Get current camera frame
- `game_start`: Initialize game session
- `game_input`: Submit gesture input
- `game_state`: Query current game state

### Why MediaPipe over YOLO?

MediaPipe was chosen for hand gesture recognition because:
- ✅ **21 precise hand landmarks** - perfect for finger-based gestures
- ✅ **Specialized for hands** - optimized models, fast inference
- ✅ **Lightweight** - runs well on Jetson and CPU
- ✅ **No training needed** - works out of the box
- ✅ **3D coordinates** - normalized (x, y, z) positions

YOLO would be better for multi-object detection (hands + game objects), but MediaPipe excels at the specific task of hand gesture recognition.

