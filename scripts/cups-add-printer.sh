#!/usr/bin/env bash
# Drucker-Warteschlange in CUPS anlegen (IP statt Hostname aus Drucker-Web-UI).
set -euo pipefail
cd "$(dirname "$0")/.."

PRINTER_IP="${1:?Drucker-IP angeben, z. B. ./scripts/cups-add-printer.sh 192.168.10.50}"
NAME="${2:-Zentrale}"
URI="ipp://${PRINTER_IP}/ipp/print"

echo "==> Lege Drucker „${NAME}“ an: ${URI}"
if ! docker compose exec cups lpadmin -p "$NAME" -E -v "$URI" -m everywhere 2>/dev/null; then
  URI="ipps://${PRINTER_IP}/ipp/print"
  echo "==> IPP fehlgeschlagen, versuche IPPS: ${URI}"
  docker compose exec cups lpadmin -p "$NAME" -E -v "$URI" -m everywhere \
    || docker compose exec cups lpadmin -p "$NAME" -E -v "$URI"
fi
docker compose exec cups cupsenable "$NAME"
docker compose exec cups cupsaccept "$NAME"
docker compose exec cups lpadmin -p "$NAME" -o printer-is-shared=true 2>/dev/null || true

echo "==> Warteschlangen:"
docker compose exec cups lpstat -p
