"""
Hand tracking module using OpenCV and MediaPipe Hands.
Captures camera input and extracts hand landmarks.
"""
import cv2
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from dataclasses import dataclass
from typing import Optional, List
import numpy as np
import os
import urllib.request
import threading
import time


@dataclass
class HandLandmarks:
    """Container for hand landmark data."""
    landmarks: List[tuple]  # 21 landmarks, each (x, y, z) in normalized coords
    confidence: float
    handedness: str  # 'Right' or 'Left'


def _get_hand_landmark_model_path():
    """Download hand landmark model if not already present."""
    model_dir = os.path.join(os.path.dirname(__file__), "models")
    os.makedirs(model_dir, exist_ok=True)
    
    model_path = os.path.join(model_dir, "hand_landmarker.task")
    
    if not os.path.exists(model_path):
        print("Downloading hand landmark model...")
        model_url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
        urllib.request.urlretrieve(model_url, model_path)
        print(f"Model downloaded to {model_path}")
    
    return model_path


class HandTracker:
    """
    Handles webcam capture and hand landmark detection.
    Uses MediaPipe Hands for single-hand detection.
    """
    
    def __init__(self, camera_id: int = 0, flip_horizontal: bool = True, use_gpu: bool = True):
        """
        Initialize hand tracker.
        
        Args:
            camera_id: Webcam index (0 for default)
            flip_horizontal: Mirror the frame for natural interaction
            use_gpu: Try to use GPU acceleration (auto-detects availability)
        """
        self.camera_id = camera_id
        self.flip_horizontal = flip_horizontal
        self.cap: Optional[cv2.VideoCapture] = None
        
        # Initialize MediaPipe Hands with new Tasks API
        model_path = _get_hand_landmark_model_path()
        base_options = python.BaseOptions(model_asset_path=model_path)
        
        # Try GPU delegate on supported platforms (Jetson, Android)
        if use_gpu:
            try:
                # Check if we're on Jetson or have GPU support
                import platform
                is_jetson = os.path.exists('/etc/nv_tegra_release') or 'tegra' in platform.platform().lower()
                
                if is_jetson:
                    # Use GPU delegate for Jetson
                    base_options.delegate = python.BaseOptions.Delegate.GPU
                    print("[HandTracker] GPU delegate enabled (Jetson detected)")
                else:
                    print("[HandTracker] GPU not available, using CPU")
            except Exception as e:
                print(f"[HandTracker] GPU init failed, using CPU: {e}")
        
        options = vision.HandLandmarkerOptions(
            base_options=base_options,
            num_hands=1,  # Single hand only
            min_hand_detection_confidence=0.3,  # Lower = faster but may have false positives
            min_hand_presence_confidence=0.3
        )
        self.hand_landmarker = vision.HandLandmarker.create_from_options(options)
        self.frame_skip_count = 0
        
        # Adjust settings based on GPU availability
        if base_options.delegate == python.BaseOptions.Delegate.GPU:
            self.frame_skip_interval = 1  # Process every frame on GPU
            self.detection_scale = 1.0  # Full resolution on GPU
            self._min_detection_interval = 1.0 / 30.0  # 30 FPS
        else:
            self.frame_skip_interval = 2  # Skip frames on CPU
            self.detection_scale = 0.6  # Downscale on CPU
            self._min_detection_interval = 1.0 / 25.0  # 25 FPS
        
        self.last_hand_landmarks: Optional[HandLandmarks] = None
        self._last_detection_time = 0.0
        self._lock = threading.Lock()
        self._latest_frame: Optional[np.ndarray] = None
        self._running = True
        self._thread = threading.Thread(target=self._worker_loop, daemon=True)
        self._thread.start()

    def _worker_loop(self) -> None:
        """Background thread for camera capture and detection."""
        while self._running:
            try:
                if self.cap is None or not self.cap.isOpened():
                    self.cap = cv2.VideoCapture(self.camera_id)
                    # Set camera properties for stability and speed
                    self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, 320)
                    self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 240)
                    self.cap.set(cv2.CAP_PROP_FPS, 30)
                    self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)  # Drop old frames
                    # Prefer MJPG if supported (faster capture)
                    try:
                        self.cap.set(cv2.CAP_PROP_FOURCC, cv2.VideoWriter_fourcc(*"MJPG"))
                    except Exception:
                        pass

                success, frame = self.cap.read() if self.cap is not None else (False, None)
                if not success or frame is None:
                    time.sleep(0.005)
                    continue

                if self.flip_horizontal:
                    frame = cv2.flip(frame, 1)

                # Update latest frame
                with self._lock:
                    self._latest_frame = frame
                
                # Run detection periodically
                now = time.time()
                if now - self._last_detection_time >= self._min_detection_interval:
                    # Skip detection on some frames to reduce lag
                    if self.frame_skip_interval > 1:
                        self.frame_skip_count = (self.frame_skip_count + 1) % self.frame_skip_interval
                    else:
                        self.frame_skip_count = 0

                    if self.frame_skip_count == 0:
                        if self.detection_scale != 1.0:
                            small = cv2.resize(frame, (0, 0), fx=self.detection_scale, fy=self.detection_scale, interpolation=cv2.INTER_AREA)
                            rgb_frame = cv2.cvtColor(small, cv2.COLOR_BGR2RGB)
                        else:
                            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
                        detection_result = self.hand_landmarker.detect(mp_image)

                        if detection_result.hand_landmarks and detection_result.handedness:
                            landmarks_list = detection_result.hand_landmarks[0]
                            handedness = detection_result.handedness[0][0].category_name
                            confidence = detection_result.handedness[0][0].score
                            landmarks = [(lm.x, lm.y, lm.z) for lm in landmarks_list]
                            with self._lock:
                                self.last_hand_landmarks = HandLandmarks(
                                    landmarks=landmarks,
                                    confidence=confidence,
                                    handedness=handedness
                                )
                    self._last_detection_time = now
            except Exception:
                time.sleep(0.005)
        
    def get_frame_and_landmarks(self) -> tuple[Optional[np.ndarray], Optional[HandLandmarks]]:
        """
        Capture frame and detect hand landmarks.
        
        Returns:
            (frame, hand_landmarks) - frame is BGR, or (None, None) on failure
        """
        with self._lock:
            frame = self._latest_frame.copy() if self._latest_frame is not None else None
            hand_landmarks = self.last_hand_landmarks
        return frame, hand_landmarks
    
    def release(self):
        """Release camera resources."""
        self._running = False
        try:
            if self._thread.is_alive():
                self._thread.join(timeout=1.0)
        except Exception:
            pass
        if self.cap is not None:
            self.cap.release()
    
    def __del__(self):
        """Cleanup on deletion."""
        try:
            self.release()
        except Exception:
            pass
