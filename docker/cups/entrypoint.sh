#!/bin/bash
set -euo pipefail

PASS="${CUPS_ADMIN_PASSWORD:-print}"

mkdir -p /run/cups
chown root:lp /run/cups 2>/dev/null || true
chmod 775 /run/cups 2>/dev/null || true

if ! id print >/dev/null 2>&1; then
  useradd -r -G lp,lpadmin print 2>/dev/null || useradd -G lp,lpadmin print
fi
echo "print:${PASS}" | chpasswd
usermod -aG lpadmin print 2>/dev/null || true

echo "CUPS-Web-UI: Benutzer print, Passwort = CUPS_ADMIN_PASSWORD (Standard: print)"

share_printers() {
  for p in $(lpstat -p 2>/dev/null | sed -n 's/^printer \(.*\) is.*/\1/p'); do
    echo "CUPS: Drucker „${p}“ für Remote-Zugriff freigeben."
    lpadmin -p "$p" -o printer-is-shared=true 2>/dev/null || true
    lpadmin -p "$p" -o auth-info-required=false 2>/dev/null || true
    cupsaccept "$p" 2>/dev/null || true
    cupsenable "$p" 2>/dev/null || true
  done
}

python3 /print-relay.py &
RELAY_PID=$!

/usr/sbin/cupsd -f &
CUPSD_PID=$!

for _ in $(seq 1 45); do
  if lpstat -r >/dev/null 2>&1; then
    share_printers
    break
  fi
  sleep 1
done

wait "$CUPSD_PID"
kill "$RELAY_PID" 2>/dev/null || true
