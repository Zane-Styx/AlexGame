"""
Gesture recognition module using rule-based landmark analysis.
Maps hand landmarks to gesture IDs without ML training.
Enhanced with vectorized operations for optimal performance.
"""
from typing import Optional, Tuple
from dataclasses import dataclass
import numpy as np


@dataclass
class GestureResult:
    """Result of gesture recognition."""
    gesture_id: Optional[int]  # 1=Peace, 2=OK, 4=HighFive
    gesture_name: str
    confidence: float
    is_valid: bool  # Whether gesture meets stability threshold


class GestureRecognizer:
    """
    Rule-based gesture recognizer using hand landmarks.
    
    Gestures:
    1 = Peace: Index and middle finger up, others down
    2 = OK: Thumb and index touching, others up
    4 = HighFive: All fingers up, palm open
    5 = Fist: All fingers down/curled
    6 = Point: Only index finger up
    7 = Rock: Index and pinky up (rock/horn gesture)
    10 = ILoveYou: Thumb, index, and pinky up
    """
    
    GESTURE_NAMES = {
        None: "None",
        1: "Peace",
        2: "OK",
        4: "HighFive",
        5: "Fist",
        6: "Point",
        7: "Rock",
        10: "ILoveYou"
    }
    
    # Landmark indices
    THUMB_TIP = 4
    INDEX_TIP = 8
    MIDDLE_TIP = 12
    RING_TIP = 16
    PINKY_TIP = 20
    
    THUMB_IP = 2
    INDEX_PIP = 6
    MIDDLE_PIP = 10
    RING_PIP = 14
    PINKY_PIP = 18
    
    PALM_CENTER = 0
    
    def __init__(self, confidence_threshold: float = 0.75, enable_smoothing: bool = True):
        """
        Initialize gesture recognizer.
        
        Args:
            confidence_threshold: Min confidence for gesture detection (0-1)
            enable_smoothing: Enable temporal smoothing for more stable results
        """
        self.confidence_threshold = confidence_threshold
        self.enable_smoothing = enable_smoothing
        self.previous_gesture = None
        self.consecutive_count = 0
        self.stability_frames = 5 if enable_smoothing else 1  # Require 5 frames (~150ms at 30fps)
        
        # Temporal smoothing buffer
        self.gesture_history = []  # Last N gestures
        self.history_size = 5
        self.confidence_history = []
    
    def recognize(self, landmarks: list[Tuple[float, float, float]]) -> GestureResult:
        """
        Recognize gesture from hand landmarks.
        
        Args:
            landmarks: List of 21 (x, y, z) tuples (normalized coords 0-1)
        
        Returns:
            GestureResult with gesture ID and confidence
        """
        if not landmarks or len(landmarks) < 21:
            return GestureResult(None, "None", 0.0, False)
        
        # Calculate distances and angles for gesture detection
        gesture_id, confidence = self._classify_gesture(landmarks)
        gesture_name = self.GESTURE_NAMES.get(gesture_id, "Unknown")
        
        # Apply stability filter (gesture must be consistent for ~0.5s)
        is_stable = self._check_stability(gesture_id, confidence)
        
        return GestureResult(gesture_id, gesture_name, confidence, is_stable)
    
    def _check_stability(self, gesture_id: Optional[int], confidence: float) -> bool:
        """
        Check if gesture is stable over time.
        Returns True only after gesture is held for stability_frames.
        """
        if confidence <= self.confidence_threshold:
            self.previous_gesture = gesture_id
            self.consecutive_count = 0
            return False

        if gesture_id == self.previous_gesture:
            self.consecutive_count += 1
        else:
            self.previous_gesture = gesture_id
            self.consecutive_count = 1

        return self.consecutive_count >= self.stability_frames
    
    def _classify_gesture(self, landmarks: list) -> Tuple[Optional[int], float]:
        """
        Classify gesture from landmarks with enhanced detection.
        
        Returns:
            (gesture_id, confidence) - gesture_id is 1-10 or None
        """
        # Get finger positions relative to palm
        fingers_up = self._get_fingers_up(landmarks)
        touch_ok = self._check_ok_touch(landmarks)
        thumb_up, index_up, middle_up, ring_up, pinky_up = fingers_up
        
        # Calculate confidence based on finger clarity
        base_confidence = self._calculate_gesture_confidence(landmarks, fingers_up)
        
        # Priority order: More specific gestures first
        
        # OK: Thumb and index touch, middle/ring/pinky up
        if touch_ok and middle_up and ring_up and pinky_up:
            return 2, min(0.90 * base_confidence, 0.95)
        
        # ILoveYou: Thumb, index, and pinky up; middle and ring down
        if thumb_up and index_up and pinky_up and not middle_up and not ring_up:
            return 10, min(0.91 * base_confidence, 0.94)
        
        # Rock: Index and pinky up, others down
        if index_up and pinky_up and not middle_up and not ring_up and not thumb_up:
            return 7, min(0.89 * base_confidence, 0.93)
        
        # Peace: Index and middle up, ring and pinky down
        if index_up and middle_up and not ring_up and not pinky_up:
            return 1, min(0.93 * base_confidence, 0.95)
        
        # Point: Only index finger up
        if index_up and not middle_up and not ring_up and not pinky_up and not thumb_up:
            return 6, min(0.92 * base_confidence, 0.95)
        
        # HighFive: All fingers up, hand open
        if all(fingers_up):
            return 4, min(0.92 * base_confidence, 0.95)
        
        # Fist: All fingers down
        if not any(fingers_up):
            return 5, min(0.90 * base_confidence, 0.94)
        
        return None, 0.0
    
    def _get_fingers_up(self, landmarks: list) -> list[bool]:
        """
        Determine which fingers are extended using vectorized operations.
        
        Returns:
            [thumb_up, index_up, middle_up, ring_up, pinky_up]
        """
        # Convert to numpy for faster operations
        if isinstance(landmarks, list):
            landmarks_array = np.array(landmarks, dtype=np.float32)
        else:
            landmarks_array = landmarks
        
        fingers_up = []
        
        # Thumb: special case, check x-direction (more strict)
        thumb_extended = landmarks_array[self.THUMB_TIP, 0] < landmarks_array[self.THUMB_IP, 0] - 0.025
        fingers_up.append(bool(thumb_extended))
        
        # Other fingers: y-direction (tip above PIP) - vectorized (stricter threshold)
        finger_tips = np.array([self.INDEX_TIP, self.MIDDLE_TIP, self.RING_TIP, self.PINKY_TIP])
        finger_pips = np.array([self.INDEX_PIP, self.MIDDLE_PIP, self.RING_PIP, self.PINKY_PIP])
        
        # Vectorized comparison
        tip_y = landmarks_array[finger_tips, 1]
        pip_y = landmarks_array[finger_pips, 1]
        extended = tip_y < (pip_y - 0.025)
        
        fingers_up.extend(extended.tolist())
        return fingers_up
    
    def _check_ok_touch(self, landmarks: list) -> bool:
        """Check if thumb and index finger are touching (OK gesture)."""
        thumb_tip = landmarks[self.THUMB_TIP]
        index_tip = landmarks[self.INDEX_TIP]
        
        distance = (
            (thumb_tip[0] - index_tip[0]) ** 2 +
            (thumb_tip[1] - index_tip[1]) ** 2
        ) ** 0.5
        
        # Touching if distance < 0.035 (normalized coords) - stricter threshold
        return distance < 0.035
    
    def _calculate_gesture_confidence(self, landmarks: list, fingers_up: list) -> float:
        """Calculate confidence score based on landmark quality and finger extension clarity."""
        # Check if fingers are clearly extended or clearly down (no ambiguity)
        confidence = 1.0
        
        finger_tips = [self.INDEX_TIP, self.MIDDLE_TIP, self.RING_TIP, self.PINKY_TIP]
        finger_pips = [self.INDEX_PIP, self.MIDDLE_PIP, self.RING_PIP, self.PINKY_PIP]
        
        for i, (tip_idx, pip_idx) in enumerate(zip(finger_tips, finger_pips)):
            y_diff = abs(landmarks[tip_idx][1] - landmarks[pip_idx][1])
            # Strongly penalize ambiguous positions (close to threshold)
            if 0.015 < y_diff < 0.035:
                confidence *= 0.85
            elif 0.01 < y_diff < 0.015 or 0.035 < y_diff < 0.04:
                confidence *= 0.75
        
        # Additional penalty for gestures near the image edge (less reliable)
        palm = landmarks[self.PALM_CENTER]
        if palm[0] < 0.1 or palm[0] > 0.9 or palm[1] < 0.1 or palm[1] > 0.9:
            confidence *= 0.90
        
        return confidence
    
    def reset_stability(self):
        """Reset stability counter (for new game session)."""
        self.previous_gesture = None
        self.consecutive_count = 0
        self.gesture_history.clear()
        self.confidence_history.clear()
