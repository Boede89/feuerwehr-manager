#!/usr/bin/env python3
"""Lokaler Druck in ffm_cups — umgeht Remote-lp-Probleme (0k, angehalten)."""
import os
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_PRINTER = os.environ.get("CUPS_PRINTER", "Zentrale")
PORT = int(os.environ.get("PRINT_RELAY_PORT", "8766"))


class PrintHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        if self.path.split("?", 1)[0] != "/print":
            self.send_error(404, "Not found")
            return
        printer = DEFAULT_PRINTER
        if "?" in self.path:
            for part in self.path.split("?", 1)[1].split("&"):
                if part.startswith("printer="):
                    printer = part.split("=", 1)[1]
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_error(400, "Invalid Content-Length")
            return
        if length < 50:
            self.send_error(400, "Body too small")
            return
        data = self.rfile.read(length)
        if len(data) < 50:
            self.send_error(400, "Incomplete body")
            return
        doc_format = "application/pdf"
        if data.startswith(b"%!PS"):
            doc_format = "application/postscript"
        cmd = [
            "lp",
            "-d",
            printer,
            "-o",
            "job-hold-until=no-hold",
            "-o",
            f"document-format={doc_format}",
            "-o",
            "media=A4",
            "-",
        ]
        proc = subprocess.run(cmd, input=data, capture_output=True, text=False)
        if proc.returncode != 0:
            err = (proc.stderr or proc.stdout or b"lp failed").decode("utf-8", errors="replace")
            self.send_error(500, err[:500])
            return
        out = (proc.stdout or b"ok").decode("utf-8", errors="replace").strip()
        body = out.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), PrintHandler)
    print(f"Print-Relay auf Port {PORT}, Standard-Drucker: {DEFAULT_PRINTER}", flush=True)
    server.serve_forever()
