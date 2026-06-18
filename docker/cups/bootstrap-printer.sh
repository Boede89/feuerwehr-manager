#!/bin/bash
set -euo pipefail

if [ -z "${CUPS_PRINTER_URI:-}" ]; then
  echo "CUPS_PRINTER_URI nicht gesetzt — Drucker manuell unter http://<host>:631 anlegen (Benutzer: print)."
  exit 0
fi

NAME="${CUPS_PRINTER_NAME:-Zentrale}"
SERVER="${CUPS_SERVER:-print:print@cups:631}"
export CUPS_SERVER="$SERVER"

echo "Warte auf CUPS ($SERVER) …"
for _ in $(seq 1 45); do
  if lpstat -r >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! lpstat -r >/dev/null 2>&1; then
  echo "CUPS nicht erreichbar — Bootstrap abgebrochen."
  exit 1
fi

if lpstat -p "$NAME" >/dev/null 2>&1; then
  echo "Drucker „$NAME“ existiert bereits."
  exit 0
fi

echo "Lege Drucker „$NAME“ an: $CUPS_PRINTER_URI"
if ! lpadmin -p "$NAME" -E -v "$CUPS_PRINTER_URI" -m everywhere 2>/dev/null; then
  lpadmin -p "$NAME" -E -v "$CUPS_PRINTER_URI"
fi
cupsenable "$NAME"
cupsaccept "$NAME"
echo "Drucker „$NAME“ bereit."
