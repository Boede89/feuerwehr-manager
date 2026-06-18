#!/bin/bash
set -euo pipefail

PASS="${CUPS_ADMIN_PASSWORD:-print}"
echo "print:${PASS}" | chpasswd
exec /usr/sbin/cupsd -f
