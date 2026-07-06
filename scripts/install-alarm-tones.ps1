# Kopiert MP3-Alarmtöne nach android/einsatzapp/app/src/main/res/raw/
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$src = Join-Path $root "android\einsatzapp\alarm-tones-source"
$dst = Join-Path $root "android\einsatzapp\app\src\main\res\raw"

if (-not (Test-Path $src)) {
    Write-Error "Ordner nicht gefunden: $src"
}

New-Item -ItemType Directory -Force -Path $dst | Out-Null

$files = Get-ChildItem -Path $src -Filter "*.mp3" -File
if ($files.Count -eq 0) {
    Write-Host "Keine MP3-Dateien in $src — bitte Dateien ablegen und Skript erneut starten."
    exit 0
}

function Convert-ToRawName([string]$baseName) {
    $n = $baseName.ToLower()
    $n = $n -replace '[^a-z0-9]+', '_'
    $n = $n -replace '_+', '_'
    $n = $n.Trim('_')
    if (-not $n.StartsWith('tone_')) { $n = "tone_$n" }
    return $n
}

foreach ($f in $files) {
    $raw = Convert-ToRawName $f.BaseName
    $target = Join-Path $dst "$raw.mp3"
    Copy-Item -Path $f.FullName -Destination $target -Force
    Write-Host "OK: $($f.Name) -> $raw.mp3"
}

Write-Host ""
Write-Host "Fertig. App in Android Studio neu bauen (Rebuild Project)."
