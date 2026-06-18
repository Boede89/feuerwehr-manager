#!/bin/bash
set -euo pipefail

PASS="${CUPS_ADMIN_PASSWORD:-print}"
mkdir -p /run/cups
chown root:lp /run/cups 2>/dev/null || true
chmod 775 /run/cups 2>/dev/null || true

echo "print:${PASS}" | chpasswd
usermod -aG lpadmin print 2>/dev/null || true

echo "CUPS-Benutzer print: Passwort aus CUPS_ADMIN_PASSWORD gesetzt."
echo "Gruppen print: $(id print)"

exec /usr/sbin/cupsd -f
