import base64
import json
import os
import socketserver
import threading
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
        jpeg_quality = int(payload.get("quality", 70))

        frame = None
        hand_landmarks = None

        if HandTracker is not None:
            frame, hand_landmarks = _get_frame_from_hand_tracker(camera_id)

        if frame is None:
            with _camera_lock:
                global _camera
                if _camera is None or not _camera.isOpened():
                    _camera = cv2.VideoCapture(camera_id)
                    _camera.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
                    _camera.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
                    _camera.set(cv2.CAP_PROP_FPS, 30)

                if _camera is None or not _camera.isOpened():
                    return {"ok": False, "error": "camera_open_failed"}

                ok, frame = _camera.read()
                if not ok or frame is None:
                    return {"ok": False, "error": "camera_read_failed"}

        gesture_payload = {}
        if hand_landmarks is not None and GestureRecognizer is not None:
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
            return {
                "ok": True,
                "data": {
                    "stats": _game.get_stats(),
                    "sequence": _game.get_sequence_display(),
                    "expected": _game.get_next_expected(),
                    "progress": _game.get_progress(),
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
            return {
                "ok": True,
                "data": {
                    "result": result,
                    "stats": _game.get_stats(),
                    "expected": _game.get_next_expected(),
                    "progress": _game.get_progress(),
                },
            }

    def _game_state(self) -> Dict[str, Any]:
        if GestureMemoryGame is None:
            return {"ok": False, "error": "game_logic_not_available"}

        with _game_lock:
            if _game is None:
                return {"ok": False, "error": "game_not_started"}

            return {
                "ok": True,
                "data": {
                    "stats": _game.get_stats(),
                    "sequence": _game.get_sequence_display(),
                    "expected": _game.get_next_expected(),
                    "progress": _game.get_progress(),
                },
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
            _hand_tracker = HandTracker(camera_id=camera_id)
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


def run(host: str = "127.0.0.1", port: int = 9009) -> None:
    with ThreadedTCPServer((host, port), JsonLineHandler) as server:
        _start_debug_view_if_enabled()
        print(f"[python-bridge] listening on {host}:{port}")
        server.serve_forever()


if __name__ == "__main__":
    run()
