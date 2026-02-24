import base64
import json
import os
import socketserver
import threading
import time
from typing import Any, Dict, Optional

try:
    import cv2  # type: ignore
    import numpy as np  # type: ignore
except Exception:
    cv2 = None
    np = None

try:
    from vision.hand_tracker import HandTracker
except Exception:
    HandTracker = None

try:
    from vision.gesture_recognizer import GestureRecognizer
except Exception:
    GestureRecognizer = None

try:
    from game.game_logic import GestureMemoryGame
except Exception:
    GestureMemoryGame = None


_camera_lock = threading.Lock()
_camera: Optional["cv2.VideoCapture"] = None

_hand_tracker_lock = threading.Lock()
_hand_tracker: Optional["HandTracker"] = None
_hand_tracker_camera_id: Optional[int] = None

_gesture_lock = threading.Lock()
_gesture_recognizer: Optional["GestureRecognizer"] = None

_debug_thread: Optional[threading.Thread] = None
_debug_running = False

_game_lock = threading.Lock()
_game: Optional["GestureMemoryGame"] = None


class JsonLineHandler(socketserver.StreamRequestHandler):
    """Simple JSON-over-TCP line protocol.

    Client sends a single-line JSON object per request.
    Server replies with a single-line JSON object per response.
    """

    def handle(self) -> None:
        while True:
            line = self.rfile.readline()
            if not line:
                break

            try:
                request = json.loads(line.decode("utf-8").strip())
            except Exception as exc:
                self._send({"ok": False, "error": f"invalid_json: {exc}"})
                continue

            response = self.dispatch(request)
            self._send(response)

    def dispatch(self, request: Dict[str, Any]) -> Dict[str, Any]:
        method = request.get("method")
        payload = request.get("payload", {})

        if method == "ping":
            return {"ok": True, "data": "pong"}

        if method == "process_frame":
            return self._process_frame(payload)

        if method == "get_frame":
            return self._get_frame(payload)

        if method == "game_start":
            return self._game_start(payload)

        if method == "game_input":
            return self._game_input(payload)

        if method == "game_state":
            return self._game_state()

        return {"ok": False, "error": f"unknown_method: {method}"}

    def _process_frame(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        image_b64 = payload.get("image_b64")
        if not image_b64:
            return {"ok": False, "error": "missing image_b64"}

        if cv2 is None or np is None:
            return {"ok": False, "error": "opencv_not_available"}

        try:
            raw = base64.b64decode(image_b64)
            data = np.frombuffer(raw, dtype=np.uint8)
            frame = cv2.imdecode(data, cv2.IMREAD_COLOR)
            if frame is None:
                return {"ok": False, "error": "invalid_image"}

            height, width = frame.shape[:2]
            return {
                "ok": True,
                "data": {
                    "width": int(width),
                    "height": int(height),
                },
            }
        except Exception as exc:
            return {"ok": False, "error": f"process_error: {exc}"}

    def _get_frame(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        if cv2 is None:
            return {"ok": False, "error": "opencv_not_available"}

        camera_id = int(payload.get("camera_id", 0))
        jpeg_quality = int(payload.get("quality", 35))
        draw_skeleton = bool(payload.get("draw_skeleton", False))
        max_width = int(payload.get("max_width", 320) or 320)
        max_height = int(payload.get("max_height", 240) or 240)

        frame = None
        hand_landmarks = None

        if HandTracker is not None:
            frame, hand_landmarks = _get_frame_from_hand_tracker(camera_id)

        if frame is None:
            with _camera_lock:
                global _camera
                if _camera is None or not _camera.isOpened():
                    _camera = cv2.VideoCapture(camera_id)
                    _camera.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
                    _camera.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
                    _camera.set(cv2.CAP_PROP_FPS, 30)
                    _camera.set(cv2.CAP_PROP_BUFFERSIZE, 1)
                    try:
                        _camera.set(cv2.CAP_PROP_FOURCC, cv2.VideoWriter_fourcc(*"MJPG"))
                    except Exception:
                        pass

                if _camera is None or not _camera.isOpened():
                    return {"ok": False, "error": "camera_open_failed"}

                ok, frame = _camera.read()
                if not ok or frame is None:
                    return {"ok": False, "error": "camera_read_failed"}

        gesture_payload = {}
        hand_landmarks_payload = []
        if hand_landmarks is not None:
            if GestureRecognizer is not None:
                with _gesture_lock:
                    global _gesture_recognizer
                    if _gesture_recognizer is None:
                        _gesture_recognizer = GestureRecognizer()
                    result = _gesture_recognizer.recognize(hand_landmarks)
                    gesture_payload = {
                        "gesture_id": result.gesture_id,
                        "gesture_name": result.gesture_name,
                        "confidence": float(result.confidence),
                        "is_valid": bool(result.is_valid),
                    }
            # Convert landmarks to normalized coordinates
            height, width = frame.shape[:2]
            for lm in hand_landmarks:
                hand_landmarks_payload.append([float(lm[0]), float(lm[1])])

        if draw_skeleton and hand_landmarks is not None:
            _draw_hand_skeleton(frame, hand_landmarks)

        # Optional downscale for faster encoding/transfer
        if max_width > 0 or max_height > 0:
            height, width = frame.shape[:2]
            target_w = max_width if max_width > 0 else width
            target_h = max_height if max_height > 0 else height
            scale = min(target_w / width, target_h / height)
            if scale > 0 and scale < 1:
                new_w = max(1, int(width * scale))
                new_h = max(1, int(height * scale))
                frame = cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_AREA)

        try:
            encode_params = [int(cv2.IMWRITE_JPEG_QUALITY), jpeg_quality]
            ok, buffer = cv2.imencode(".jpg", frame, encode_params)
            if not ok:
                return {"ok": False, "error": "jpeg_encode_failed"}

            b64 = base64.b64encode(buffer.tobytes()).decode("utf-8")
            return {
                "ok": True,
                "data": {
                    "image_b64": b64,
                    "width": int(frame.shape[1]),
                    "height": int(frame.shape[0]),
                    "gesture": gesture_payload,
                    "hand_landmarks": hand_landmarks_payload,
                },
            }
        except Exception as exc:
            return {"ok": False, "error": f"frame_error: {exc}"}

    def _send(self, obj: Dict[str, Any]) -> None:
        data = json.dumps(obj, separators=(",", ":")) + "\n"
        self.wfile.write(data.encode("utf-8"))

    def _game_start(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        if GestureMemoryGame is None:
            return {"ok": False, "error": "game_logic_not_available"}

        difficulty = payload.get("difficulty", "medium")
        with _game_lock:
            global _game
            _game = GestureMemoryGame(str(difficulty))
            stats = _game.get_stats()
            return {
                "ok": True,
                "data": {
                    "stats": stats,
                    "sequence": _game.get_sequence_display(),
                    "expected": _game.get_next_expected(),
                    "progress": _game.get_progress(),
                    "time_remaining": stats.get('time_remaining', 15.0),
                },
            }

    def _game_input(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        if GestureMemoryGame is None:
            return {"ok": False, "error": "game_logic_not_available"}

        with _game_lock:
            if _game is None:
                return {"ok": False, "error": "game_not_started"}

            gesture_id = payload.get("gesture_id")
            result = _game.input_gesture(gesture_id)
            stats = _game.get_stats()
            
            response_data = {
                "result": result,
                "stats": stats,
                "expected": _game.get_next_expected(),
                "progress": _game.get_progress(),
                "time_remaining": stats.get('time_remaining', 15.0),
            }
            
            # If a new sequence started, include it
            if result.get('new_sequence'):
                response_data['sequence'] = result['new_sequence']
            
            return {
                "ok": True,
                "data": response_data,
            }

    def _game_state(self) -> Dict[str, Any]:
        if GestureMemoryGame is None:
            return {"ok": False, "error": "game_logic_not_available"}

        with _game_lock:
            if _game is None:
                return {"ok": False, "error": "game_not_started"}

            # Check for timeout and handle it if needed
            timeout_result = _game.check_and_handle_timeout()
            
            stats = _game.get_stats()
            
            response_data = {
                "stats": stats,
                "sequence": _game.get_sequence_display(),
                "expected": _game.get_next_expected(),
                "progress": _game.get_progress(),
                "time_remaining": stats.get('time_remaining', 15.0),
            }
            
            # If timeout occurred, include new sequence info
            if timeout_result.get('timeout_occurred'):
                response_data['timeout_occurred'] = True
                response_data['sequence'] = timeout_result['new_sequence']
            
            return {
                "ok": True,
                "data": response_data,
            }


def _get_frame_from_hand_tracker(camera_id: int):
    if HandTracker is None:
        return None, None

    with _hand_tracker_lock:
        global _hand_tracker, _hand_tracker_camera_id
        if _hand_tracker is None or _hand_tracker_camera_id != camera_id:
            try:
                if _hand_tracker is not None:
                    _hand_tracker.release()
            except Exception:
                pass
            _hand_tracker = HandTracker(
                camera_id=camera_id,
                flip_horizontal=True,
                use_gpu=True,
                adaptive_performance=True,
                enable_roi_tracking=False,      # ← Modify these
                enable_kalman_filter=False,     # ← for your needs
                motion_detection=False
            )
            _hand_tracker_camera_id = camera_id

        frame, hand_data = _hand_tracker.get_frame_and_landmarks()
        landmarks = hand_data.landmarks if hand_data is not None else None
        return frame, landmarks


def _draw_hand_skeleton(frame, landmarks):
    if cv2 is None or not landmarks:
        return
    height, width = frame.shape[:2]
    points = [(int(lm[0] * width), int(lm[1] * height)) for lm in landmarks]

    connections = [
        (0, 1), (1, 2), (2, 3), (3, 4),
        (0, 5), (5, 6), (6, 7), (7, 8),
        (0, 9), (9, 10), (10, 11), (11, 12),
        (0, 13), (13, 14), (14, 15), (15, 16),
        (0, 17), (17, 18), (18, 19), (19, 20),
        (5, 9), (9, 13), (13, 17)
    ]

    for a, b in connections:
        if a < len(points) and b < len(points):
            cv2.line(frame, points[a], points[b], (0, 255, 0), 2)

    for p in points:
        cv2.circle(frame, p, 3, (0, 0, 255), -1)


def _start_debug_view_if_enabled() -> None:
    if cv2 is None or HandTracker is None:
        return
    enabled = os.environ.get("PYTHON_DEBUG_VIEW", "0") == "1"
    if not enabled:
        return

    global _debug_thread, _debug_running
    if _debug_thread is not None and _debug_thread.is_alive():
        return

    _debug_running = True

    def _loop():
        while _debug_running:
            frame = None
            hand_landmarks = None
            try:
                with _hand_tracker_lock:
                    global _hand_tracker, _hand_tracker_camera_id
                    if _hand_tracker is None:
                        _hand_tracker = HandTracker(camera_id=0)
                        _hand_tracker_camera_id = 0
                    frame, hand_data = _hand_tracker.get_frame_and_landmarks()
                    if hand_data is not None:
                        hand_landmarks = hand_data.landmarks
            except Exception:
                frame = None

            if frame is None:
                cv2.waitKey(1)
                continue

            if hand_landmarks:
                _draw_hand_skeleton(frame, hand_landmarks)

            cv2.imshow("Python Hand Debug", frame)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

        try:
            cv2.destroyWindow("Python Hand Debug")
        except Exception:
            pass

    _debug_thread = threading.Thread(target=_loop, name="PythonHandDebug", daemon=True)
    _debug_thread.start()


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True


def run(host: str = "0.0.0.0", port: int = 9009) -> None:
    with ThreadedTCPServer((host, port), JsonLineHandler) as server:
        _start_debug_view_if_enabled()
        print(f"[python-bridge] listening on {host}:{port} (threaded)")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\n[PythonBridge] Shutting down...")
            global _debug_running
            _debug_running = False


if __name__ == "__main__":
    run()
