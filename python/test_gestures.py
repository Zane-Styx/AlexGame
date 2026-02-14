"""
Test script for enhanced gesture recognition.
Displays camera feed with detected gestures and performance metrics.
"""
import sys
import time

try:
    import cv2
    import numpy as np
except ImportError:
    print("Error: OpenCV not installed. Run: pip install opencv-python")
    sys.exit(1)

try:
    from vision.hand_tracker import HandTracker
    from vision.gesture_recognizer import GestureRecognizer
    from vision.performance_monitor import PerformanceMonitor
except ImportError as e:
    print(f"Error importing modules: {e}")
    sys.exit(1)


def draw_landmarks_on_frame(frame, landmarks, handedness):
    """Draw hand landmarks on frame for visualization."""
    h, w = frame.shape[:2]
    
    # Draw connections between landmarks
    connections = [
        # Thumb
        (0, 1), (1, 2), (2, 3), (3, 4),
        # Index
        (0, 5), (5, 6), (6, 7), (7, 8),
        # Middle
        (0, 9), (9, 10), (10, 11), (11, 12),
        # Ring
        (0, 13), (13, 14), (14, 15), (15, 16),
        # Pinky
        (0, 17), (17, 18), (18, 19), (19, 20),
        # Palm
        (5, 9), (9, 13), (13, 17)
    ]
    
    # Draw lines
    for start_idx, end_idx in connections:
        if start_idx < len(landmarks) and end_idx < len(landmarks):
            start_point = landmarks[start_idx]
            end_point = landmarks[end_idx]
            start_pos = (int(start_point[0] * w), int(start_point[1] * h))
            end_pos = (int(end_point[0] * w), int(end_point[1] * h))
            cv2.line(frame, start_pos, end_pos, (0, 255, 0), 2)
    
    # Draw points
    for landmark in landmarks:
        x, y = int(landmark[0] * w), int(landmark[1] * h)
        cv2.circle(frame, (x, y), 5, (255, 0, 0), -1)
    
    # Draw handedness label
    cv2.putText(frame, f"{handedness} Hand", (10, 30),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)


def main():
    """Main test loop."""
    print("=" * 60)
    print("Enhanced Gesture Recognition Test")
    print("=" * 60)
    print("\nInitializing hand tracker and gesture recognizer...")
    
    # Initialize with enhanced features
    tracker = HandTracker(
        camera_id=0,
        flip_horizontal=True,
        use_gpu=True,
        adaptive_performance=True
    )
    
    recognizer = GestureRecognizer(
        confidence_threshold=0.6,
        enable_smoothing=True
    )
    
    monitor = PerformanceMonitor(window_size=60)
    
    print("✓ Initialization complete!")
    print("\nSupported Gestures:")
    print("  1. Peace      ✌️  - Index + middle up")
    print("  2. OK         👌 - Thumb + index touch")
    print("  4. HighFive   🖐️  - All fingers up")
    print("  5. Fist       ✊ - All fingers closed")
    print("  6. Point      👉 - Index finger only")
    print("  7. Rock       🤘 - Index + pinky up")
    print("  10. ILoveYou  🤟 - Thumb + index + pinky")
    print("\nPress 'q' to quit, 's' to show stats, 'r' to reset stats")
    print("=" * 60)
    
    time.sleep(1)  # Give tracker time to initialize
    
    last_stats_time = time.time()
    show_stats = False
    
    try:
        while True:
            monitor.record_frame()
            
            # Get frame and landmarks
            frame, hand_data = tracker.get_frame_and_landmarks()
            
            if frame is None:
                monitor.record_dropped_frame()
                continue
            
            # Create display frame
            display_frame = frame.copy()
            h, w = display_frame.shape[:2]
            
            # Detect gesture
            gesture_result = None
            if hand_data and hand_data.landmarks:
                # Draw landmarks
                draw_landmarks_on_frame(
                    display_frame,
                    hand_data.landmarks,
                    hand_data.handedness
                )
                
                # Recognize gesture
                gesture_result = recognizer.recognize(hand_data.landmarks)
                monitor.record_gesture(gesture_result.gesture_id if gesture_result.is_valid else None)
                
                # Display gesture info
                if gesture_result.is_valid:
                    gesture_text = f"Gesture: {gesture_result.gesture_name}"
                    confidence_text = f"Confidence: {gesture_result.confidence:.2f}"
                    
                    # Large gesture name
                    cv2.putText(display_frame, gesture_text, (10, h - 80),
                               cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 255, 0), 3)
                    cv2.putText(display_frame, confidence_text, (10, h - 40),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
                else:
                    cv2.putText(display_frame, "No stable gesture", (10, h - 40),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 200), 2)
            else:
                cv2.putText(display_frame, "No hand detected", (10, h - 40),
                           cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 200), 2)
            
            # Display FPS
            fps = monitor.get_fps()
            cv2.putText(display_frame, f"FPS: {fps:.1f}", (w - 150, 30),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 0), 2)
            
            # Display detailed stats if enabled
            if show_stats:
                stats = monitor.get_stats()
                y_offset = 60
                stats_text = [
                    f"Latency: {stats['detection_latency_ms']:.1f}ms",
                    f"Stability: {stats['gesture_stability']:.2f}",
                    f"Drops: {stats['drop_rate']:.1%}",
                    f"Detections: {stats['gesture_detections']}"
                ]
                for text in stats_text:
                    cv2.putText(display_frame, text, (w - 200, y_offset),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
                    y_offset += 25
            
            # Show frame
            cv2.imshow('Enhanced Gesture Recognition Test', display_frame)
            
            # Handle keyboard input
            key = cv2.waitKey(1) & 0xFF
            if key == ord('q'):
                break
            elif key == ord('s'):
                show_stats = not show_stats
                print(f"Stats display: {'ON' if show_stats else 'OFF'}")
            elif key == ord('r'):
                monitor.reset()
                print("Stats reset!")
            
            # Print stats every 10 seconds
            if time.time() - last_stats_time > 10:
                print("\n--- Performance Update ---")
                stats = monitor.get_stats()
                print(f"FPS: {stats['fps']:.1f} | "
                      f"Latency: {stats['detection_latency_ms']:.1f}ms | "
                      f"Stability: {stats['gesture_stability']:.2f} | "
                      f"Gestures: {stats['gesture_detections']}")
                last_stats_time = time.time()
    
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
    
    finally:
        print("\n" + "=" * 60)
        print("Final Performance Statistics")
        print("=" * 60)
        monitor.print_stats()
        
        # Cleanup
        tracker.release()
        cv2.destroyAllWindows()
        print("✓ Cleanup complete. Goodbye!")


if __name__ == "__main__":
    main()
