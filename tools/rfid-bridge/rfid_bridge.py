#!/usr/bin/env python3
"""
Lokale RFID-Bridge für Feuerwehr-Manager (HTTP ohne Web Serial).

Liest Chip-UIDs vom seriellen Lesegerät (z. B. YSoft USB Reader im COM-Modus)
und stellt sie der Login-Seite unter http://127.0.0.1:<port>/api/v1/card bereit.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Optional

try:
    import serial
    from serial import SerialException
except ImportError:
    print(
        "Modul 'serial' fehlt. Installieren mit:\n"
        "  sudo apt install python3-serial\n"
        "oder: pip install pyserial",
        file=sys.stderr,
    )
    sys.exit(1)

DEFAULT_HTTP_PORT = 18765
DEFAULT_BAUD = 9600
UID_PATTERN = re.compile(r"[0-9A-Fa-f]{4,128}")
SERIAL_CANDIDATES = ("/dev/ttyACM0", "/dev/ttyACM1", "/dev/ttyUSB0", "/dev/ttyUSB1")


def normalize_uid(raw: str) -> Optional[str]:
    cleaned = re.sub(r"[\s:\-]", "", raw.strip()).upper()
    if UID_PATTERN.fullmatch(cleaned):
        return cleaned
    match = UID_PATTERN.search(cleaned)
    return match.group(0).upper() if match else None


class CardState:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.last_uid: Optional[str] = None
        self.last_at: float = 0.0
        self.serial_port: Optional[str] = None
        self.serial_error: Optional[str] = None

    def set_uid(self, uid: str) -> None:
        with self._lock:
            self.last_uid = uid
            self.last_at = time.time()

    def snapshot(self) -> dict:
        with self._lock:
            return {
                "cardUid": self.last_uid,
                "readAt": self.last_at if self.last_uid else None,
                "serialPort": self.serial_port,
                "serialError": self.serial_error,
            }


def detect_serial_port(explicit: Optional[str]) -> str:
    if explicit:
        return explicit
    for candidate in SERIAL_CANDIDATES:
        try:
            with serial.Serial(candidate, DEFAULT_BAUD, timeout=0.2):
                return candidate
        except (SerialException, OSError):
            continue
    raise SystemExit(
        "Kein Seriell-Port gefunden. Bitte --device /dev/ttyACM0 angeben."
    )


def serial_reader_loop(device: str, baud: int, state: CardState, stop: threading.Event) -> None:
    buffer = ""
    while not stop.is_set():
        try:
            with serial.Serial(device, baud, timeout=0.25) as port:
                state.serial_port = device
                state.serial_error = None
                while not stop.is_set():
                    chunk = port.read(256)
                    if not chunk:
                        continue
                    buffer += chunk.decode("ascii", errors="ignore")
                    while "\n" in buffer or "\r" in buffer:
                        for sep in ("\r\n", "\n", "\r"):
                            if sep in buffer:
                                line, buffer = buffer.split(sep, 1)
                                break
                        else:
                            break
                        uid = normalize_uid(line)
                        if uid:
                            state.set_uid(uid)
                            print(f"[rfid-bridge] Chip gelesen: {uid}", flush=True)
        except (SerialException, OSError) as exc:
            state.serial_error = str(exc)
            print(f"[rfid-bridge] Seriell-Fehler ({device}): {exc}", flush=True)
            time.sleep(2.0)


def make_handler(state: CardState):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *args) -> None:
            return

        def _cors(self) -> None:
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")

        def do_OPTIONS(self) -> None:
            self.send_response(204)
            self._cors()
            self.end_headers()

        def do_GET(self) -> None:
            path = self.path.split("?", 1)[0]
            if path == "/api/v1/health":
                body = {"ok": True, **state.snapshot()}
            elif path == "/api/v1/card":
                snap = state.snapshot()
                body = {
                    "cardUid": snap["cardUid"],
                    "readAt": snap["readAt"],
                }
            else:
                self.send_response(404)
                self._cors()
                self.end_headers()
                return

            payload = json.dumps(body).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self._cors()
            self.end_headers()
            self.wfile.write(payload)

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description="Feuerwehr-Manager RFID-Bridge")
    parser.add_argument("--device", help="Seriell-Gerät, z. B. /dev/ttyACM0")
    parser.add_argument("--baud", type=int, default=DEFAULT_BAUD)
    parser.add_argument("--http-port", type=int, default=DEFAULT_HTTP_PORT)
    parser.add_argument("--bind", default="127.0.0.1")
    args = parser.parse_args()

    device = detect_serial_port(args.device)
    state = CardState()
    stop = threading.Event()

    reader = threading.Thread(
        target=serial_reader_loop,
        args=(device, args.baud, state, stop),
        daemon=True,
    )
    reader.start()

    server = ThreadingHTTPServer((args.bind, args.http_port), make_handler(state))
    print(
        f"[rfid-bridge] Gestartet: {device} @ {args.baud} Baud, "
        f"HTTP http://{args.bind}:{args.http_port}/api/v1/card",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[rfid-bridge] Beendet.", flush=True)
    finally:
        stop.set()
        server.shutdown()


if __name__ == "__main__":
    main()
