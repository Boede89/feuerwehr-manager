#!/usr/bin/env bash
# Kopiert MP3-Alarmtöne nach android/einsatzapp/app/src/main/res/raw/
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${ROOT}/android/einsatzapp/alarm-tones-source"
DST="${ROOT}/android/einsatzapp/app/src/main/res/raw"

if [[ ! -d "$SRC" ]]; then
  echo "Ordner nicht gefunden: $SRC" >&2
  exit 1
fi

mkdir -p "$DST"
shopt -s nullglob
files=("$SRC"/*.mp3)
if [[ ${#files[@]} -eq 0 ]]; then
  echo "Keine MP3-Dateien in $SRC — bitte Dateien ablegen und Skript erneut starten."
  exit 0
fi

to_raw_name() {
  local n
  n=$(echo "$1" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/_/g' | sed 's/__*/_/g' | sed 's/^_\|_$//g')
  [[ "$n" == tone_* ]] || n="tone_${n}"
  echo "$n"
}

for f in "${files[@]}"; do
  base=$(basename "$f" .mp3)
  raw=$(to_raw_name "$base")
  cp -f "$f" "${DST}/${raw}.mp3"
  echo "OK: $(basename "$f") -> ${raw}.mp3"
done

echo ""
echo "Fertig. App in Android Studio neu bauen (Rebuild Project)."
