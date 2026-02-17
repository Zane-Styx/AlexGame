"""
Hand tracking module using OpenCV and MediaPipe Hands.
Captures camera input and extracts hand landmarks.
Enhanced with ROI tracking, Kalman filtering, and motion detection for optimal performance.
"""
import cv2
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from dataclasses import dataclass, field
from typing import Optional, List
import numpy as np
import os
import urllib.request
import threading
import time

try:
    from filterpy.kalman import KalmanFilter
except ImportError:
    KalmanFilter = None


@dataclass
class HandLandmarks:
    """Container for hand landmark data."""
    landmarks: List[tuple]  # 21 landmarks, each (x, y, z) in normalized coords
    confidence: float
    handedness: str  # 'Right' or 'Left'
    roi: Optional[tuple] = None  # (x, y, w, h) region of interest


class KalmanLandmarkFilter:
    """Kalman filter for smooth landmark tracking without lag."""
    
    def __init__(self, num_landmarks: int = 21):
        """Initialize Kalman filters for each landmark coordinate."""
        self.num_landmarks = num_landmarks
        self.filters = []
        self.initialized = False
        
        if KalmanFilter is not None:
            # Create 2D Kalman filter for each landmark (x, y)
            for _ in range(num_landmarks):
                kf = KalmanFilter(dim_x=4, dim_z=2)  # state: [x, vx, y, vy], measure: [x, y]
                # State transition matrix (constant velocity model)
                kf.F = np.array([[1, 1, 0, 0],
                                [0, 1, 0, 0],
                                [0, 0, 1, 1],
                                [0, 0, 0, 1]], dtype=np.float32)
                # Measurement function
                kf.H = np.array([[1, 0, 0, 0],
                                [0, 0, 1, 0]], dtype=np.float32)
                # Measurement noise (low = trust measurements)
                kf.R *= 0.008
                # Process noise (low = smooth motion)
                kf.Q *= 0.0005
                # Initial covariance
                kf.P *= 10
                self.filters.append(kf)
    
    def update(self, landmarks: np.ndarray) -> np.ndarray:
        """Apply Kalman filtering to landmarks for smoothness."""
        if not self.filters:
            return landmarks
        
        if not self.initialized:
            # Initialize filters with first measurement
            for i, kf in enumerate(self.filters):
                x, y = landmarks[i, 0], landmarks[i, 1]
                kf.x = np.array([x, 0, y, 0], dtype=np.float32)
            self.initialized = True
            return landmarks
        
        # Update and predict (vectorized where possible)
        filtered = landmarks.copy()
        for i, kf in enumerate(self.filters):
            # Update with measurement
            z = np.array([landmarks[i, 0], landmarks[i, 1]], dtype=np.float32)
            kf.predict()
            kf.update(z)
            
            # Extract filtered position
            filtered[i, 0] = kf.x[0]
            filtered[i, 1] = kf.x[2]
        
        return filtered

    def reset(self):
        """Reset all filters."""
        self.initialized = False


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
    
    def __init__(self, camera_id: int = 0, flip_horizontal: bool = True, use_gpu: bool = True, 
                 adaptive_performance: bool = True, enable_roi_tracking: bool = True,
                 enable_kalman_filter: bool = True, motion_detection: bool = True):
        """
        Initialize hand tracker.
        
        Args:
            camera_id: Webcam index (0 for default)
            flip_horizontal: Mirror the frame for natural interaction
            use_gpu: Try to use GPU acceleration (auto-detects availability)
            adaptive_performance: Dynamically adjust FPS based on detection load
            enable_roi_tracking: Track hand in Region of Interest (major speedup)
            enable_kalman_filter: Use Kalman filtering for smooth landmarks
            motion_detection: Skip detection when hand is stable (saves CPU)
        """
        self.camera_id = camera_id
        self.flip_horizontal = flip_horizontal
        self.adaptive_performance = adaptive_performance
        self.enable_roi_tracking = enable_roi_tracking
        self.enable_kalman_filter = enable_kalman_filter and KalmanFilter is not None
        self.motion_detection = motion_detection
        self.cap: Optional[cv2.VideoCapture] = None
        
        # ROI tracking state
        self.current_roi: Optional[tuple] = None  # (x, y, w, h)
        self.roi_expand_ratio = 0.3  # Expand ROI by 30% for safety
        self.full_detection_interval = 15  # Full frame detection every N frames
        self.frames_since_full_detection = 0
        
        # Motion detection state
        self.previous_landmarks: Optional[np.ndarray] = None
        self.motion_threshold = 0.02  # Normalize coordinate threshold
        self.stable_frames = 0
        self.max_stable_skip = 3  # Skip detection for max 3 frames when stable
        
        # Kalman filter
        self.kalman_filter = KalmanLandmarkFilter() if self.enable_kalman_filter else None
        
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
            min_hand_detection_confidence=0.5,  # Balanced threshold
            min_hand_presence_confidence=0.5,
            min_tracking_confidence=0.5  # Smoother tracking
        )
        self.hand_landmarker = vision.HandLandmarker.create_from_options(options)
        self.frame_skip_count = 0
        
        # Performance tracking for adaptive optimization
        self.detection_times = []
        self.max_detection_samples = 30
        self.last_adjustment_time = time.time()
        
        # Frame preprocessing cache
        self.preprocessing_cache = {}
        self.cache_valid_frames = 3
        
        # Adjust settings based on GPU availability
        if base_options.delegate == python.BaseOptions.Delegate.GPU:
            self.frame_skip_interval = 1  # Process every frame on GPU
            self.detection_scale = 1.0  # Full resolution on GPU
            self._min_detection_interval = 1.0 / 30.0  # 30 FPS
            self._target_fps = 30
        else:
            self.frame_skip_interval = 2  # Skip frames on CPU
            self.detection_scale = 0.7  # Moderate downscale on CPU
            self._min_detection_interval = 1.0 / 25.0  # 25 FPS
            self._target_fps = 25
        
        self.last_hand_landmarks: Optional[HandLandmarks] = None
        self.last_hand_seen_time = 0.0
        self.hand_presence_timeout = 0.35
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
                    detection_start = time.time()
                    
                    # Skip detection on some frames to reduce lag
                    if self.frame_skip_interval > 1:
                        self.frame_skip_count = (self.frame_skip_count + 1) % self.frame_skip_interval
                    else:
                        self.frame_skip_count = 0

                    if self.frame_skip_count == 0:
                        # Check if we should skip detection due to stable hand
                        should_detect = True
                        if self.motion_detection and self.stable_frames < self.max_stable_skip:
                            if self.previous_landmarks is not None and self._is_hand_stable():
                                should_detect = False
                                self.stable_frames += 1
                        
                        if should_detect:
                            self.stable_frames = 0
                            self.frames_since_full_detection += 1
                            
                            # Decide whether to use ROI or full frame
                            use_roi = (self.enable_roi_tracking and 
                                      self.current_roi is not None and 
                                      self.frames_since_full_detection < self.full_detection_interval)
                            
                            if use_roi:
                                # Extract and process only ROI (major speedup)
                                processing_frame = self._extract_roi(frame)
                            else:
                                # Full frame detection
                                processing_frame = frame
                                self.frames_since_full_detection = 0
                            
                            # Resize if needed
                            if self.detection_scale != 1.0:
                                processing_frame = cv2.resize(processing_frame, (0, 0), 
                                                             fx=self.detection_scale, 
                                                             fy=self.detection_scale, 
                                                             interpolation=cv2.INTER_LINEAR)
                            
                            # Convert to RGB (vectorized operation)
                            rgb_frame = cv2.cvtColor(processing_frame, cv2.COLOR_BGR2RGB)
                            
                            # Enhance contrast for better detection in varying lighting
                            rgb_frame = self._enhance_frame(rgb_frame)
                            
                            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
                            detection_result = self.hand_landmarker.detect(mp_image)
                        
                            # Track detection time for adaptive performance
                            detection_time = time.time() - detection_start
                            if self.adaptive_performance:
                                self._update_performance_metrics(detection_time)

                            if detection_result.hand_landmarks and detection_result.handedness:
                                landmarks_list = detection_result.hand_landmarks[0]
                                handedness = detection_result.handedness[0][0].category_name
                                confidence = detection_result.handedness[0][0].score
                                
                                # Convert to numpy array for vectorized operations
                                landmarks_array = np.array([(lm.x, lm.y, lm.z) for lm in landmarks_list], 
                                                          dtype=np.float32)
                                
                                # Adjust landmarks if using ROI
                                if use_roi and self.current_roi is not None:
                                    landmarks_array = self._adjust_landmarks_from_roi(landmarks_array)
                                
                                # Apply Kalman filtering if enabled
                                if self.kalman_filter is not None:
                                    landmarks_array = self.kalman_filter.update(landmarks_array)
                                
                                # Update ROI for next frame
                                if self.enable_roi_tracking:
                                    self.current_roi = self._calculate_roi_from_landmarks(landmarks_array, frame.shape)
                                
                                # Store for motion detection
                                self.previous_landmarks = landmarks_array.copy()
                                
                                # Convert back to list of tuples
                                landmarks = [(float(x), float(y), float(z)) for x, y, z in landmarks_array]
                                
                                with self._lock:
                                    self.last_hand_landmarks = HandLandmarks(
                                        landmarks=landmarks,
                                        confidence=confidence,
                                        handedness=handedness,
                                        roi=self.current_roi
                                    )
                                    self.last_hand_seen_time = now
                            else:
                                # No hand detected - clear if hand has been missing for a short window
                                if now - self.last_hand_seen_time > self.hand_presence_timeout:
                                    with self._lock:
                                        self.last_hand_landmarks = None
                                    self.current_roi = None
                                    if self.kalman_filter is not None:
                                        self.kalman_filter.reset()
                                    self.previous_landmarks = None
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
            if self.last_hand_landmarks is not None:
                if time.time() - self.last_hand_seen_time > self.hand_presence_timeout:
                    self.last_hand_landmarks = None
            hand_landmarks = self.last_hand_landmarks
        return frame, hand_landmarks
    
    def _enhance_frame(self, rgb_frame: np.ndarray) -> np.ndarray:
        """Apply subtle enhancement for better detection in varying lighting."""
        # Convert to LAB color space for better lighting normalization
        lab = cv2.cvtColor(rgb_frame, cv2.COLOR_RGB2LAB)
        l, a, b = cv2.split(lab)
        
        # Apply CLAHE (Contrast Limited Adaptive Histogram Equalization) to L channel
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        l = clahe.apply(l)
        
        # Merge and convert back
        enhanced_lab = cv2.merge([l, a, b])
        enhanced_rgb = cv2.cvtColor(enhanced_lab, cv2.COLOR_LAB2RGB)
        return enhanced_rgb
    
    def _is_hand_stable(self) -> bool:
        """Check if hand has remained stable (low motion)."""
        if self.previous_landmarks is None or self.last_hand_landmarks is None:
            return False
        
        current_landmarks = np.array(self.last_hand_landmarks.landmarks, dtype=np.float32)[:, :2]
        prev_landmarks = self.previous_landmarks[:, :2]
        
        # Calculate mean absolute difference (vectorized)
        diff = np.abs(current_landmarks - prev_landmarks)
        mean_motion = np.mean(diff)
        
        return mean_motion < self.motion_threshold
    
    def _extract_roi(self, frame: np.ndarray) -> np.ndarray:
        """Extract Region of Interest from frame."""
        if self.current_roi is None:
            return frame
        
        x, y, w, h = self.current_roi
        h_frame, w_frame = frame.shape[:2]
        
        # Clamp to frame boundaries
        x = max(0, min(x, w_frame - 1))
        y = max(0, min(y, h_frame - 1))
        w = min(w, w_frame - x)
        h = min(h, h_frame - y)
        
        return frame[y:y+h, x:x+w]
    
    def _calculate_roi_from_landmarks(self, landmarks: np.ndarray, frame_shape: tuple) -> tuple:
        """Calculate ROI bounding box from landmarks with expansion."""
        h, w = frame_shape[:2]
        
        # Get min/max coordinates (vectorized)
        x_coords = landmarks[:, 0]
        y_coords = landmarks[:, 1]
        
        min_x, max_x = np.min(x_coords), np.max(x_coords)
        min_y, max_y = np.min(y_coords), np.max(y_coords)
        
        # Convert to pixel coordinates
        min_x = int(min_x * w)
        max_x = int(max_x * w)
        min_y = int(min_y * h)
        max_y = int(max_y * h)
        
        # Expand ROI
        width = max_x - min_x
        height = max_y - min_y
        expand_w = int(width * self.roi_expand_ratio)
        expand_h = int(height * self.roi_expand_ratio)
        
        # Calculate expanded ROI
        roi_x = max(0, min_x - expand_w)
        roi_y = max(0, min_y - expand_h)
        roi_w = min(w - roi_x, width + 2 * expand_w)
        roi_h = min(h - roi_y, height + 2 * expand_h)
        
        return (roi_x, roi_y, roi_w, roi_h)
    
    def _adjust_landmarks_from_roi(self, landmarks: np.ndarray) -> np.ndarray:
        """Adjust landmark coordinates from ROI space back to full frame space."""
        if self.current_roi is None:
            return landmarks
        
        roi_x, roi_y, roi_w, roi_h = self.current_roi
        
        # Adjust x and y coordinates (vectorized)
        landmarks[:, 0] = (landmarks[:, 0] * roi_w + roi_x) / self.cap.get(cv2.CAP_PROP_FRAME_WIDTH)
        landmarks[:, 1] = (landmarks[:, 1] * roi_h + roi_y) / self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
        
        return landmarks
    
    def _update_performance_metrics(self, detection_time: float):
        """Track detection performance and adaptively adjust settings."""
        self.detection_times.append(detection_time)
        if len(self.detection_times) > self.max_detection_samples:
            self.detection_times.pop(0)
        
        # Adjust settings every 5 seconds
        now = time.time()
        if now - self.last_adjustment_time > 5.0 and len(self.detection_times) >= 10:
            avg_time = sum(self.detection_times) / len(self.detection_times)
            current_fps = 1.0 / avg_time if avg_time > 0 else self._target_fps
            
            # If running too slow, reduce quality
            if current_fps < self._target_fps * 0.8:
                if self.detection_scale > 0.5:
                    self.detection_scale = max(0.5, self.detection_scale - 0.1)
                    print(f"[HandTracker] Reduced scale to {self.detection_scale:.1f} (FPS: {current_fps:.1f})")
            # If running well, increase quality
            elif current_fps > self._target_fps * 1.2:
                if self.detection_scale < 1.0:
                    self.detection_scale = min(1.0, self.detection_scale + 0.1)
                    print(f"[HandTracker] Increased scale to {self.detection_scale:.1f} (FPS: {current_fps:.1f})")
            
            self.last_adjustment_time = now
    
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
