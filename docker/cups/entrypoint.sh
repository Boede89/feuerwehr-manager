#!/bin/bash
set -euo pipefail

PASS="${CUPS_ADMIN_PASSWORD:-print}"
mkdir -p /run/cups
chown root:lp /run/cups 2>/dev/null || true
chmod 775 /run/cups 2>/dev/null || true

echo "print:${PASS}" | chpasswd
usermod -aG lpadmin print 2>/dev/null || true

echo "CUPS-Benutzer print: Passwort aus CUPS_ADMIN_PASSWORD gesetzt."

share_printers() {
  cupsctl --remote-any --share-printers --remote-admin 2>/dev/null || true
  for p in $(lpstat -p 2>/dev/null | sed -n 's/^printer \(.*\) is.*/\1/p'); do
    echo "CUPS: Drucker „${p}“ für Remote-Zugriff freigeben."
    lpadmin -p "$p" -o printer-is-shared=true 2>/dev/null || true
    lpadmin -p "$p" -o auth-info-required=false 2>/dev/null || true
    cupsaccept "$p" 2>/dev/null || true
    cupsenable "$p" 2>/dev/null || true
  done
}

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
