"""
Performance benchmark script for gesture recognition system.
Tests different configurations and measures FPS, CPU usage, and latency.
"""
import time
import sys
import numpy as np

try:
    from vision.hand_tracker import HandTracker
    from vision.gesture_recognizer import GestureRecognizer
    from vision.performance_monitor import PerformanceMonitor
except ImportError as e:
    print(f"Error importing modules: {e}")
    sys.exit(1)


def benchmark_configuration(name, tracker_config, duration=10):
    """Benchmark a specific configuration."""
    print(f"\n{'='*60}")
    print(f"Testing: {name}")
    print(f"{'='*60}")
    
    tracker = HandTracker(**tracker_config)
    recognizer = GestureRecognizer()
    monitor = PerformanceMonitor()
    
    print(f"Running for {duration} seconds...")
    time.sleep(2)  # Let tracker initialize and start capturing
    
    start_time = time.time()
    last_frame = None
    frame_count = 0
    gesture_count = 0
    last_update = start_time
    
    try:
        while time.time() - start_time < duration:
            frame, hand_data = tracker.get_frame_and_landmarks()
            
            # Check if we got a new frame (not cached)
            if frame is not None and (last_frame is None or not np.array_equal(frame, last_frame)):
                monitor.record_frame()
                frame_count += 1
                last_frame = frame.copy() if frame is not None else None
                
                if hand_data and hand_data.landmarks:
                    gesture_result = recognizer.recognize(hand_data.landmarks)
                    if gesture_result.is_valid:
                        monitor.record_gesture(gesture_result.gesture_id)
                        gesture_count += 1
            else:
                # No new frame, add small delay to prevent CPU spinning
                time.sleep(0.001)
            
            # Print progress every 3 seconds
            now = time.time()
            if now - last_update >= 3.0:
                elapsed = now - start_time
                current_fps = frame_count / elapsed if elapsed > 0 else 0
                print(f"  Progress: {elapsed:.1f}s, FPS: {current_fps:.1f}, Gestures: {gesture_count}")
                last_update = now
    
    except KeyboardInterrupt:
        print("\nBenchmark interrupted")
    finally:
        tracker.release()
    
    # Calculate metrics
    elapsed = time.time() - start_time
    avg_fps = frame_count / elapsed if elapsed > 0 else 0
    stats = monitor.get_stats()
    
    # Print results
    print(f"\n{name} Results:")
    print(f"  Duration: {elapsed:.1f}s")
    print(f"  Frames Processed: {frame_count}")
    print(f"  Average FPS: {avg_fps:.1f}")
    print(f"  Gesture Detections: {stats['gesture_detections']}")
    print(f"  Gesture Changes: {stats['gesture_changes']}")
    print(f"  Stability Score: {stats['gesture_stability']:.2f}")
    
    if stats['gesture_detections'] > 0:
        change_rate = stats['gesture_changes'] / stats['gesture_detections']
        if change_rate < 0.1:
            print(f"  ✓ Very stable gestures (change rate: {change_rate:.1%})")
        elif change_rate < 0.3:
            print(f"  ✓ Stable gestures (change rate: {change_rate:.1%})")
        else:
            print(f"  ⚠ Unstable gestures (change rate: {change_rate:.1%})")
    
    return {
        'name': name,
        'fps': avg_fps,
        'gestures': stats['gesture_detections'],
        'gesture_changes': stats['gesture_changes'],
        'stability': stats['gesture_stability'],
        'frames': frame_count
    }


def main():
    """Run performance benchmarks."""
    print("="*60)
    print("Gesture Recognition Performance Benchmark")
    print("="*60)
    print("\nThis will test different configurations to find optimal settings.")
    print("⚠ IMPORTANT: Show your hand to the camera and make gestures")
    print("during each test for accurate measurements!")
    print("\nPress Ctrl+C to skip a test.")
    
    duration = 20  # seconds per test
    results = []
    
    # Test configurations
    configurations = [
        {
            'name': 'Maximum Performance (All Features)',
            'config': {
                'use_gpu': True,
                'adaptive_performance': True,
                'enable_roi_tracking': True,
                'enable_kalman_filter': True,
                'motion_detection': True
            }
        },
        {
            'name': 'ROI Only (No Kalman/Motion)',
            'config': {
                'use_gpu': True,
                'adaptive_performance': True,
                'enable_roi_tracking': True,
                'enable_kalman_filter': False,
                'motion_detection': False
            }
        },
        {
            'name': 'Kalman Only (No ROI/Motion)',
            'config': {
                'use_gpu': True,
                'adaptive_performance': True,
                'enable_roi_tracking': False,
                'enable_kalman_filter': True,
                'motion_detection': False
            }
        },
        {
            'name': 'Baseline (No Optimizations)',
            'config': {
                'use_gpu': True,
                'adaptive_performance': True,
                'enable_roi_tracking': False,
                'enable_kalman_filter': False,
                'motion_detection': False
            }
        },
        {
            'name': 'CPU Mode (All Features)',
            'config': {
                'use_gpu': False,
                'adaptive_performance': True,
                'enable_roi_tracking': True,
                'enable_kalman_filter': True,
                'motion_detection': True
            }
        }
    ]
    
    print(f"\n{len(configurations)} configurations to test (~{duration}s each)")
    print(f"Total estimated time: ~{len(configurations) * duration // 60} minutes")
    input("\n⏸ Press Enter to start benchmark...")
    
    # Run benchmarks
    for i, config in enumerate(configurations, 1):
        print(f"\n[Test {i}/{len(configurations)}]")
        try:
            result = benchmark_configuration(config['name'], config['config'], duration)
            results.append(result)
        except Exception as e:
            print(f"Error in {config['name']}: {e}")
            continue
        
        time.sleep(2)  # Brief pause between tests
    
    # Print summary
    print("\n" + "="*60)
    print("BENCHMARK SUMMARY")
    print("="*60)
    print(f"\n{'Configuration':<35} {'FPS':>8} {'Gestures':>10} {'Stability':>10}")
    print("-"*60)
    
    for result in results:
        stability_str = f"{result['stability']:.2f}" if result['gestures'] > 0 else "N/A"
        print(f"{result['name']:<35} {result['fps']:>8.1f} "
              f"{result['gestures']:>10} {stability_str:>10}")
    
    # Find best configuration
    if results:
        best_fps = max(results, key=lambda x: x['fps'])
        configs_with_gestures = [r for r in results if r['gestures'] > 0]
        best_stability = max(configs_with_gestures, key=lambda x: x['stability']) if configs_with_gestures else None
        
        print("\n" + "="*60)
        print(f"Best FPS: {best_fps['name']}")
        print(f"  → {best_fps['fps']:.1f} FPS with {best_fps['gestures']} gesture detections")
        if best_stability:
            print(f"Best Stability: {best_stability['name']}")
            print(f"  → {best_stability['stability']:.2f} score with {best_stability['gestures']} gestures")
        print("="*60)
        
        # Recommendations
        print("\n📊 Performance Analysis:")
        if best_fps['fps'] > 30:
            print("✓ Excellent performance! System can handle maximum quality.")
            print(f"  Your system achieves {best_fps['fps']:.0f} FPS - well above 30 FPS target")
        elif best_fps['fps'] > 25:
            print("✓ Good performance with recommended settings.")
            print(f"  Solid {best_fps['fps']:.0f} FPS - above 25 FPS threshold")
        elif best_fps['fps'] > 20:
            print("⚠ Acceptable performance but below optimal.")
            print(f"  {best_fps['fps']:.0f} FPS - consider CPU-optimized settings")
        else:
            print("⚠ Low performance detected.")
            print(f"  Only {best_fps['fps']:.0f} FPS - enable all optimizations")
        
        # Gesture detection quality
        if configs_with_gestures:
            total_gestures = sum(r['gestures'] for r in configs_with_gestures)
            avg_gestures = total_gestures / len(configs_with_gestures)
            
            print(f"\n🎯 Gesture Detection:")
            if avg_gestures > 0:
                print(f"✓ Detected gestures successfully (avg: {avg_gestures:.0f} per test)")
                if best_stability and best_stability['stability'] > 0.85:
                    print(f"✓ Good gesture stability ({best_stability['stability']:.2f})")
                elif best_stability:
                    print(f"⚠ Moderate stability ({best_stability['stability']:.2f}) - enable smoothing")
            else:
                print("⚠ No gestures detected - make sure to show gestures during test!")
        
        # Specific recommendations
        print("\n💡 Recommended Configuration:")
        if best_fps['name'] == "Maximum Performance (All Features)":
            print("→ Use ALL optimizations (ROI + Kalman + Motion Detection)")
            print("  This configuration gives you the best overall performance!")
        elif "ROI" in best_fps['name']:
            print("→ ROI tracking provides the biggest benefit")
            print("  Enable: enable_roi_tracking=True for best results")
        elif "Kalman" in best_fps['name']:
            print("→ Kalman filtering works well on your system")
            print("  Enable: enable_kalman_filter=True for smoother tracking")
        elif "Baseline" in best_fps['name']:
            print("→ Basic settings work best for your system")
            print("  Some optimizations may not benefit your specific hardware")
        
        # Performance tips
        if best_fps['fps'] < 25:
            print("\n⚡ Performance Tips:")
            print("  • Enable ROI tracking (biggest FPS boost)")
            print("  • Enable motion detection (reduces CPU when idle)")
            print("  • Disable Kalman if CPU-constrained")
            print("  • Close other applications")
            print("  • Ensure good lighting (reduces processing)")
    
    print("\n✅ Benchmark complete!")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nBenchmark cancelled by user.")
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
