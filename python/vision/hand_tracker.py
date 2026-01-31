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
    
    def __init__(self, camera_id: int = 0, flip_horizontal: bool = True):
        """
        Initialize hand tracker.
        
        Args:
            camera_id: Webcam index (0 for default)
            flip_horizontal: Mirror the frame for natural interaction
        """
        self.camera_id = camera_id
        self.flip_horizontal = flip_horizontal
        self.cap = cv2.VideoCapture(camera_id)
        
        # Set camera properties for stability
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
        self.cap.set(cv2.CAP_PROP_FPS, 30)
        
        # Initialize MediaPipe Hands with new Tasks API
        model_path = _get_hand_landmark_model_path()
        base_options = python.BaseOptions(model_asset_path=model_path)
        options = vision.HandLandmarkerOptions(
            base_options=base_options,
            num_hands=1,  # Single hand only
            min_hand_detection_confidence=0.5,
            min_hand_presence_confidence=0.5
        )
        self.hand_landmarker = vision.HandLandmarker.create_from_options(options)
        
    def get_frame_and_landmarks(self) -> tuple[Optional[np.ndarray], Optional[HandLandmarks]]:
        """
        Capture frame and detect hand landmarks.
        
        Returns:
            (frame, hand_landmarks) - frame is BGR, or (None, None) on failure
        """
        success, frame = self.cap.read()
        if not success:
            return None, None
        
        # Flip for natural interaction
        if self.flip_horizontal:
            frame = cv2.flip(frame, 1)
        
        # Convert BGR to RGB for MediaPipe
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        detection_result = self.hand_landmarker.detect(mp_image)
        
        hand_landmarks = None
        if detection_result.hand_landmarks and detection_result.handedness:
            # Get first (and only) hand
            landmarks_list = detection_result.hand_landmarks[0]
            # handedness is List[List[Category]], so we need [hand_index][classification_index]
            handedness = detection_result.handedness[0][0].category_name
            confidence = detection_result.handedness[0][0].score
            
            # Extract landmark coordinates (x, y, z)
            landmarks = [
                (lm.x, lm.y, lm.z) for lm in landmarks_list
            ]
            hand_landmarks = HandLandmarks(
                landmarks=landmarks,
                confidence=confidence,
                handedness=handedness
            )
        
        return frame, hand_landmarks
    
    def release(self):
        """Release camera resources."""
        self.cap.release()
    
    def __del__(self):
        """Cleanup on deletion."""

import numpy as np
