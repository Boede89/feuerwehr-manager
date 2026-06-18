#!/bin/bash
set -euo pipefail

PASS="${CUPS_ADMIN_PASSWORD:-print}"
echo "print:${PASS}" | chpasswd
echo "CUPS-Benutzer print: Passwort aus CUPS_ADMIN_PASSWORD gesetzt."
exec /usr/sbin/cupsd -f
