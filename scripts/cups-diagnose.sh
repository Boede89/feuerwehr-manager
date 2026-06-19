#!/usr/bin/env bash
# CUPS-/Druck-Diagnose pro Server (jeder Container hat eigenes Volume).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Git-Stand ==="
git log -1 --oneline 2>/dev/null || echo "(kein Git)"

echo ""
echo "=== Container ==="
docker compose ps cups app 2>/dev/null || true

echo ""
echo "=== Drucker in CUPS (lokal im Container) ==="
docker compose exec cups lpstat -p 2>&1 || echo "lpstat fehlgeschlagen"

echo ""
echo "=== Print-Relay /printers ==="
docker compose exec cups python3 -c "
import urllib.request
try:
    print(urllib.request.urlopen('http://127.0.0.1:8766/printers', timeout=5).read().decode())
except Exception as e:
    print('FEHLER:', e)
" 2>&1 || true

echo ""
echo "=== Remote lpstat aus App-Container ==="
docker compose exec app lpstat -h cups:631 -U print:print -p 2>&1 || true

echo ""
echo "=== FEUERWEHR_PRINT_RELAY_URL in App ==="
docker compose exec app printenv FEUERWEHR_PRINT_RELAY_URL 2>/dev/null || true

echo ""
echo "Hinweis: Drucker in CUPS Web-UI auf Server A erscheinen nicht automatisch auf Server B."
echo "Leer bei lpstat -p → Drucker auf DIESEM Server anlegen: ./scripts/cups-add-printer.sh <IP>"
