"""
Gesture recognition module using rule-based landmark analysis.
Maps hand landmarks to gesture IDs without ML training.
"""
from typing import Optional, Tuple
from dataclasses import dataclass


@dataclass
class GestureResult:
    """Result of gesture recognition."""
    gesture_id: Optional[int]  # 1=Peace, 2=OK, 3=Like, 4=HighFive
    gesture_name: str
    confidence: float
    is_valid: bool  # Whether gesture meets stability threshold


class GestureRecognizer:
    """
    Rule-based gesture recognizer using hand landmarks.
    
    Gestures:
    1 = Peace: Index and middle finger up, others down
    2 = OK: Thumb and index touching, others up
    3 = Like: Thumb up, other fingers down/curled
    4 = HighFive: All fingers up, palm open
    """
    
    GESTURE_NAMES = {
        None: "None",
        1: "Peace",
        2: "OK",
        3: "Like",
        4: "HighFive"
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
    
    def __init__(self, confidence_threshold: float = 0.6):
        """
        Initialize gesture recognizer.
        
        Args:
            confidence_threshold: Min confidence for gesture detection (0-1)
        """
        self.confidence_threshold = confidence_threshold
        self.previous_gesture = None
        self.consecutive_count = 0
        self.stability_frames = 1  # Immediate acceptance after first detection
    
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
        Classify gesture from landmarks.
        
        Returns:
            (gesture_id, confidence) - gesture_id is 1-4 or None
        """
        # Get finger positions relative to palm
        fingers_up = self._get_fingers_up(landmarks)
        touch_ok = self._check_ok_touch(landmarks)
        thumb_up = fingers_up[0]
        fingers = fingers_up[1:]
        
        # Peace: Index and middle up, ring and pinky down
        if fingers_up[1] and fingers_up[2] and not fingers_up[3] and not fingers_up[4]:
            return 1, 0.95
        
        # OK: Thumb and index touch, middle/ring/pinky up
        if touch_ok and fingers_up[2] and fingers_up[3] and fingers_up[4]:
            return 2, 0.90
        
        # Like: Thumb up, others down/curled
        if thumb_up and not any(fingers):
            return 3, 0.92
        
        # HighFive: All fingers up, hand open
        if all(fingers_up):
            return 4, 0.94
        
        return None, 0.0
    
    def _get_fingers_up(self, landmarks: list) -> list[bool]:
        """
        Determine which fingers are extended.
        
        Returns:
            [thumb_up, index_up, middle_up, ring_up, pinky_up]
        """
        # A finger is "up" if its tip is higher (lower y) than PIP joint
        fingers_up = []
        
        # Thumb: special case, check x-direction
        thumb_extended = landmarks[self.THUMB_TIP][0] < landmarks[self.THUMB_IP][0] - 0.02
        fingers_up.append(thumb_extended)
        
        # Other fingers: y-direction (tip above PIP)
        finger_tips = [self.INDEX_TIP, self.MIDDLE_TIP, self.RING_TIP, self.PINKY_TIP]
        finger_pips = [self.INDEX_PIP, self.MIDDLE_PIP, self.RING_PIP, self.PINKY_PIP]
        
        for tip_idx, pip_idx in zip(finger_tips, finger_pips):
            extended = landmarks[tip_idx][1] < landmarks[pip_idx][1] - 0.02
            fingers_up.append(extended)
        
        return fingers_up
    
    def _check_ok_touch(self, landmarks: list) -> bool:
        """Check if thumb and index finger are touching (OK gesture)."""
        thumb_tip = landmarks[self.THUMB_TIP]
        index_tip = landmarks[self.INDEX_TIP]
        
        distance = (
            (thumb_tip[0] - index_tip[0]) ** 2 +
            (thumb_tip[1] - index_tip[1]) ** 2
        ) ** 0.5
        
        # Touching if distance < 0.05 (normalized coords)
        return distance < 0.05
    
    def reset_stability(self):
        """Reset stability counter (for new game session)."""
        self.previous_gesture = None
        self.consecutive_count = 0
