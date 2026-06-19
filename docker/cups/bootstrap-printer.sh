#!/bin/bash
set -euo pipefail

if [ -z "${CUPS_PRINTER_URI:-}" ]; then
  if [ -n "${CUPS_PRINTER_HOST:-}" ]; then
    CUPS_PRINTER_URI="ipp://${CUPS_PRINTER_HOST}/ipp/print"
  else
    echo "CUPS_PRINTER_URI nicht gesetzt — Drucker manuell unter http://<host>:631 anlegen (Benutzer: print)."
    echo "Hinweis: Im Docker-Container Hostnamen (.local / .localdomain) oft ungültig — IP-Adresse verwenden."
    exit 0
  fi
fi

# Anführungszeichen/Leerzeichen aus Copy-Paste entfernen
CUPS_PRINTER_URI="$(printf '%s' "$CUPS_PRINTER_URI" | tr -d '\"' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

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
  if ! lpadmin -p "$NAME" -E -v "$CUPS_PRINTER_URI" 2>/dev/null; then
    # Konica/Workplace: oft IPPS statt IPP
    alt_uri="$(printf '%s' "$CUPS_PRINTER_URI" | sed 's|^ipp://|ipps://|')"
    if [ "$alt_uri" != "$CUPS_PRINTER_URI" ]; then
      echo "Versuche IPPS: $alt_uri"
      lpadmin -p "$NAME" -E -v "$alt_uri" -m everywhere 2>/dev/null || lpadmin -p "$NAME" -E -v "$alt_uri"
    else
      lpadmin -p "$NAME" -E -v "$CUPS_PRINTER_URI"
    fi
  fi
fi
cupsenable "$NAME"
cupsaccept "$NAME"
lpadmin -p "$NAME" -o printer-is-shared=true 2>/dev/null || true
echo "Drucker „$NAME“ bereit."
