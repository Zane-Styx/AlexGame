"""
Performance monitoring utilities for hand tracking and gesture recognition.
Provides real-time FPS, latency, and accuracy metrics.
"""
import time
from typing import Optional, Dict
from collections import deque


class PerformanceMonitor:
    """
    Monitor performance metrics for real-time computer vision.
    Tracks FPS, detection latency, gesture accuracy, and system load.
    """
    
    def __init__(self, window_size: int = 60):
        """
        Initialize performance monitor.
        
        Args:
            window_size: Number of samples for moving average (e.g., 60 = 1 sec at 60fps)
        """
        self.window_size = window_size
        
        # Frame timing
        self.frame_times = deque(maxlen=window_size)
        self.last_frame_time = time.time()
        
        # Detection timing
        self.detection_times = deque(maxlen=window_size)
        
        # Gesture recognition
        self.gesture_changes = 0
        self.gesture_detections = 0
        self.last_gesture = None
        
        # System metrics
        self.dropped_frames = 0
        self.total_frames = 0
        
        # Session start
        self.start_time = time.time()
    
    def record_frame(self):
        """Record a new frame for FPS calculation."""
        now = time.time()
        frame_delta = now - self.last_frame_time
        self.frame_times.append(frame_delta)
        self.last_frame_time = now
        self.total_frames += 1
    
    def record_detection(self, detection_time: float):
        """
        Record detection latency.
        
        Args:
            detection_time: Time taken for detection in seconds
        """
        self.detection_times.append(detection_time)
    
    def record_gesture(self, gesture_id: Optional[int]):
        """
        Record gesture recognition for stability tracking.
        
        Args:
            gesture_id: Detected gesture ID or None
        """
        if gesture_id is not None:
            self.gesture_detections += 1
            if gesture_id != self.last_gesture:
                self.gesture_changes += 1
                self.last_gesture = gesture_id
    
    def record_dropped_frame(self):
        """Record a dropped/skipped frame."""
        self.dropped_frames += 1
    
    def get_fps(self) -> float:
        """Get current FPS (frames per second)."""
        if not self.frame_times:
            return 0.0
        avg_frame_time = sum(self.frame_times) / len(self.frame_times)
        return 1.0 / avg_frame_time if avg_frame_time > 0 else 0.0
    
    def get_detection_latency(self) -> float:
        """Get average detection latency in milliseconds."""
        if not self.detection_times:
            return 0.0
        return (sum(self.detection_times) / len(self.detection_times)) * 1000
    
    def get_gesture_stability(self) -> float:
        """
        Get gesture stability score (0-1).
        Higher = more stable (fewer rapid changes).
        """
        if self.gesture_detections == 0:
            return 1.0
        # Lower change rate = higher stability
        change_rate = self.gesture_changes / self.gesture_detections
        return max(0.0, 1.0 - change_rate)
    
    def get_drop_rate(self) -> float:
        """Get frame drop rate (0-1)."""
        if self.total_frames == 0:
            return 0.0
        return self.dropped_frames / self.total_frames
    
    def get_uptime(self) -> float:
        """Get session uptime in seconds."""
        return time.time() - self.start_time
    
    def get_stats(self) -> Dict[str, float]:
        """
        Get all performance statistics.
        
        Returns:
            Dictionary with performance metrics
        """
        return {
            'fps': round(self.get_fps(), 1),
            'detection_latency_ms': round(self.get_detection_latency(), 1),
            'gesture_stability': round(self.get_gesture_stability(), 2),
            'drop_rate': round(self.get_drop_rate(), 3),
            'uptime_seconds': round(self.get_uptime(), 1),
            'total_frames': self.total_frames,
            'dropped_frames': self.dropped_frames,
            'gesture_detections': self.gesture_detections,
            'gesture_changes': self.gesture_changes
        }
    
    def print_stats(self):
        """Print formatted performance statistics."""
        stats = self.get_stats()
        print("\n=== Performance Monitor ===")
        print(f"FPS: {stats['fps']:.1f}")
        print(f"Detection Latency: {stats['detection_latency_ms']:.1f}ms")
        print(f"Gesture Stability: {stats['gesture_stability']:.2f}")
        print(f"Frame Drop Rate: {stats['drop_rate']:.1%}")
        print(f"Uptime: {stats['uptime_seconds']:.1f}s")
        print(f"Total Frames: {stats['total_frames']}")
        print(f"Gestures Detected: {stats['gesture_detections']}")
        print("========================\n")
    
    def reset(self):
        """Reset all metrics."""
        self.frame_times.clear()
        self.detection_times.clear()
        self.gesture_changes = 0
        self.gesture_detections = 0
        self.last_gesture = None
        self.dropped_frames = 0
        self.total_frames = 0
        self.start_time = time.time()
        self.last_frame_time = time.time()
