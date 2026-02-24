# ZealGame - Setup Guide

This guide walks you through setting up everything needed to run ZealGame, including the Python gesture recognition system and the Java game.

---

## Prerequisites

Before you begin, make sure you have the following installed:

- **Java JDK 17+** — Required to build and run the game
  - Verify: `java -version`
- **Python 3.9+** — Required for the gesture recognition bridge
  - Verify: `python --version`
- **A webcam** — Required for hand gesture tracking
- **Git** — To clone the repository (if not already done)

---

## 1. Clone the Repository

```bash
git clone https://github.com/ZaneStyx/ZealGame.git
cd ZealGame
```

---

## 2. Set Up the Python Virtual Environment

All Python dependencies are isolated in a virtual environment. Run the following commands from the **root of the project**.

### Windows

```powershell
# Create the virtual environment
python -m venv .venv

# Activate the virtual environment
.venv\Scripts\Activate.ps1
```

> If you get a script execution policy error, run this first:
> ```powershell
> Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
> ```

### macOS / Linux

```bash
# Create the virtual environment
python3 -m venv .venv

# Activate the virtual environment
source .venv/bin/activate
```

Once activated, your terminal prompt will show `(.venv)`.

---

## 3. Install Python Dependencies

With the virtual environment activated, install all required packages:

```bash
pip install -r python/requirements.txt
```

This installs:

| Package | Version | Purpose |
|---------|---------|---------|
| `opencv-python` | >=4.8.0 | Camera capture and image processing |
| `numpy` | >=1.24.0 | Vectorized math for gesture detection |
| `mediapipe` | >=0.10.0 | Hand landmark tracking |
| `filterpy` | >=1.4.5 | Kalman filtering for smooth tracking (recommended) |
| `scipy` | >=1.10.0 | Advanced filtering |
| `pytest` | >=7.4.0 | Running tests |

### Optional: Verify Installation

```bash
python -c "import cv2, numpy, mediapipe; print('All core dependencies installed successfully.')"
```

---

## 4. Test Gesture Recognition (Optional)

Before running the full game, you can test that gesture detection is working with your webcam:

```bash
cd python
python test_gestures.py
```

**Controls in the test window:**
- `q` — Quit
- `s` — Toggle stats display
- `r` — Reset statistics

---

## 5. Start the Python Bridge Server

The gesture recognition system runs as a WebSocket server that communicates with the Java game. Open a terminal with the virtual environment activated and run:

```bash
cd python
python bridge_server.py
```

The server will start on **port 8765**. Leave this terminal open while playing.

---

## 6. Build and Run the Java Game

Open a **separate terminal** (no need for the virtual environment here) and run from the project root:

### Run Directly

```bash
# Windows
gradlew.bat lwjgl3:run

# macOS / Linux
./gradlew lwjgl3:run
```

### Build a Runnable JAR

```bash
# Windows
gradlew.bat lwjgl3:jar

# macOS / Linux
./gradlew lwjgl3:jar
```

The JAR will be output to `lwjgl3/build/libs/`. Run it with:

```bash
java -jar lwjgl3/build/libs/<jar-name>.jar
```

---

## Quick Start Summary

Open **two terminals** from the project root:

**Terminal 1 — Python Bridge Server:**
```powershell
.venv\Scripts\Activate.ps1
cd python
python bridge_server.py
```

**Terminal 2 — Java Game:**
```powershell
.\gradlew.bat lwjgl3:run
```

---

## Deactivating the Virtual Environment

When you're done, deactivate the virtual environment:

```bash
deactivate
```

---

## Troubleshooting

### Python not found
Make sure Python 3.9+ is installed and added to your PATH.

### Webcam not detected
- Check that no other application is using your webcam.
- Try changing `camera_id=0` to `camera_id=1` in `python/vision/hand_tracker.py` if you have multiple cameras.

### `mediapipe` install fails
Try upgrading pip first:
```bash
python -m pip install --upgrade pip
pip install -r python/requirements.txt
```

### Game can't connect to Python server
Make sure the Python bridge server is running **before** launching the Java game, and that port 8765 is not blocked by a firewall.

### Gradle build fails
Ensure you have JDK 17+ installed and that `JAVA_HOME` is set correctly:
```powershell
# Windows
echo $env:JAVA_HOME

# macOS / Linux
echo $JAVA_HOME
```

---

## Project Structure (Relevant to Setup)

```
ZealGame/
├── python/
│   ├── requirements.txt       # Python dependencies
│   ├── bridge_server.py       # WebSocket server (start this first)
│   ├── test_gestures.py       # Gesture recognition test script
│   ├── game/                  # Game logic module
│   └── vision/                # Hand tracking & gesture recognition
├── core/                      # Java game logic (libGDX)
├── lwjgl3/                    # Desktop launcher
├── assets/                    # Game assets (sprites, UI, sounds)
├── .venv/                     # Python virtual environment (created by you)
└── SETUP.md                   # This file
```
