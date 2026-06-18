#!/usr/bin/env bash
# Komplett-Installation auf frischem Debian-LXC (Docker + App).
# Aufruf:
#   curl -fsSL https://raw.githubusercontent.com/Boede89/feuerwehr-manager/main/scripts/install-server.sh | sudo bash
# oder im geklonten Repo:
#   sudo ./scripts/install-server.sh
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/Boede89/feuerwehr-manager.git}"
INSTALL_DIR="${INSTALL_DIR:-/opt/feuerwehr-manager}"
SKIP_DOCKER=0
IN_REPO=0
FRESH_DB=0
NO_CACHE=1

usage() {
  cat <<'EOF'
Feuerwehr-Manager — Server-Installation

Aufruf:
  sudo ./scripts/install-server.sh [Optionen]

Optionen:
  --fresh          MySQL-Volume löschen (leere DB, danach SQL-Import in der Web-UI)
  --dir PATH       Installationsverzeichnis (Standard: /opt/feuerwehr-manager)
  --skip-docker    Docker nicht installieren (wenn bereits vorhanden)
  --use-cache      Docker-Build mit Cache (schneller, nicht für Erstinstallation empfohlen)
  -h, --help       Diese Hilfe

Ein-Zeilen-Installation (frischer LXC):
  curl -fsSL https://raw.githubusercontent.com/Boede89/feuerwehr-manager/main/scripts/install-server.sh | sudo bash
EOF
}

log() { printf '==> %s\n' "$*"; }
warn() { printf 'WARNUNG: %s\n' "$*" >&2; }
die() { printf 'Fehler: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fresh) FRESH_DB=1; shift ;;
    --dir) INSTALL_DIR="${2:?}"; shift 2 ;;
    --skip-docker) SKIP_DOCKER=1; shift ;;
    --in-repo) IN_REPO=1; shift ;;
    --use-cache) NO_CACHE=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unbekannte Option: $1 (siehe --help)" ;;
  esac
done

if [[ "$(id -u)" -ne 0 ]]; then
  die "Bitte als root ausführen: sudo $0"
fi

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    die "Weder 'docker compose' noch 'docker-compose' gefunden."
  fi
}

debian_codename() {
  if [[ -f /etc/os-release ]]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    case "${VERSION_CODENAME:-}" in
      bookworm|trixie|bullseye) echo "$VERSION_CODENAME"; return 0 ;;
    esac
  fi
  echo bookworm
}

install_base_packages() {
  log "Systempakete installieren (git, curl, …)"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl gnupg git openssl
  if ! timedatectl show -p Timezone --value 2>/dev/null | grep -q Europe/Berlin; then
    timedatectl set-timezone Europe/Berlin 2>/dev/null || true
  fi
}

install_docker() {
  if [[ "$SKIP_DOCKER" -eq 1 ]]; then
    return 0
  fi
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "Docker ist bereits installiert"
    return 0
  fi

  log "Docker Engine + Compose Plugin installieren"
  warn "Proxmox-LXC: Features 'nesting' und 'keyctl' müssen aktiv sein."

  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc

  local codename
  codename="$(debian_codename)"
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian ${codename} stable" \
    > /etc/apt/sources.list.d/docker.list

  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

  systemctl enable --now docker
  docker run --rm hello-world >/dev/null
  log "Docker-Test erfolgreich"
}

ensure_repository() {
  if [[ -f "${INSTALL_DIR}/docker-compose.yml" ]]; then
    log "Repository vorhanden: ${INSTALL_DIR}"
    cd "${INSTALL_DIR}"
    if [[ -d .git ]]; then
      git pull --ff-only || warn "git pull fehlgeschlagen — fahre mit lokalem Stand fort"
    fi
    return 0
  fi

  log "Repository klonen nach ${INSTALL_DIR}"
  mkdir -p "$(dirname "${INSTALL_DIR}")"
  git clone "${REPO_URL}" "${INSTALL_DIR}"
  cd "${INSTALL_DIR}"
}

write_env_value() {
  local key="$1"
  local value="$2"
  local file="$3"
  if grep -q "^${key}=" "$file"; then
    awk -v k="$key" -v v="$value" 'BEGIN { found=0 }
      $0 ~ "^" k "=" { print k "=" v; found=1; next }
      { print }
      END { if (!found) print k "=" v }' "$file" > "${file}.tmp"
    mv "${file}.tmp" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

ensure_env_file() {
  if [[ -f .env ]]; then
    log ".env existiert bereits — wird nicht überschrieben"
    return 0
  fi

  [[ -f .env.example ]] || die ".env.example fehlt im Repository"
  cp .env.example .env

  local totp audit bootstrap
  totp="$(openssl rand -base64 48 | tr -d '\n')"
  audit="$(openssl rand -base64 32 | tr -d '\n')"
  bootstrap="$(openssl rand -base64 18 | tr -d '\n/+=' | head -c 16)"

  write_env_value "FEUERWEHR_TOTP_ENCRYPTION_KEY" "$totp" .env
  write_env_value "FEUERWEHR_AUDIT_SALT" "$audit" .env
  write_env_value "FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD" "$bootstrap" .env

  log ".env erstellt (TOTP-Key, Audit-Salt, Bootstrap-Passwort)"
  warn "Bootstrap-Login: admin / Passwort steht in .env (FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD)"
}

mysql_volume_name() {
  local project
  project="$(basename "$(pwd)")"
  project="${project//./-}"
  echo "${project}_ffm_mysql_data"
}

deploy_containers() {
  if [[ "$FRESH_DB" -eq 1 ]]; then
    log "MySQL-Volume löschen (--fresh)"
    compose_cmd down -v 2>/dev/null || true
    local vol
    vol="$(mysql_volume_name)"
    docker volume rm "$vol" 2>/dev/null || true
  fi

  log "Container bauen und starten (kann mehrere Minuten dauern)"
  if [[ "$NO_CACHE" -eq 1 ]]; then
    compose_cmd build --no-cache app
    compose_cmd up -d
  else
    compose_cmd up -d --build
  fi
}

wait_for_mysql() {
  log "Warte auf MySQL (healthy) …"
  local i
  for i in $(seq 1 60); do
    if compose_cmd ps mysql 2>/dev/null | grep -q '(healthy)'; then
      return 0
    fi
    sleep 2
  done
  die "MySQL wurde nicht healthy — Logs: docker compose logs mysql"
}

app_is_up() {
  compose_cmd ps app 2>/dev/null | grep -qE 'Up [0-9]' || return 1
  curl -fsS -o /dev/null "http://127.0.0.1:8080/login" 2>/dev/null
}

wait_for_app() {
  log "Warte auf App-Start …"
  local i
  for i in $(seq 1 36); do
    if app_is_up; then
      return 0
    fi
    sleep 5
  done
  return 1
}

repair_flyway_v37_if_needed() {
  if wait_for_app; then
    return 0
  fi

  if ! compose_cmd logs app 2>&1 | grep -q "version 37"; then
    return 1
  fi

  log "Flyway V37 reparieren …"
  compose_cmd stop app || true
  compose_cmd exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-schema.sql \
    2>/dev/null || true
  compose_cmd exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-mark-success.sql
  compose_cmd up -d app
  wait_for_app
}

print_summary() {
  local ip bootstrap_pw
  ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  bootstrap_pw="$(grep '^FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD=' .env 2>/dev/null | cut -d= -f2- || echo 'siehe .env')"

  cat <<EOF

========================================
 Feuerwehr-Manager — Installation fertig
========================================

 Web-Oberfläche (empfohlen zum Test):
   http://${ip:-<Server-IP>}:8080

 HTTPS (nur mit DNS fw-manager.home.arpa):
   https://fw-manager.home.arpa

 Erster Login (wenn DB noch leer):
   Benutzer: admin
   Passwort: ${bootstrap_pw}

 Datenbank-Backup einspielen:
   Admin (global) → Import/Export → .sql hochladen

 .env sichern (TOTP-Key nicht verlieren!):
   ${INSTALL_DIR}/.env

 Logs:
   cd ${INSTALL_DIR} && docker compose logs -f app

========================================
EOF
}

main() {
  # Wenn per curl | bash: Repository noch nicht da → klonen und Skript aus Repo erneut starten
  if [[ "$IN_REPO" -eq 0 ]] && [[ ! -f "$(dirname "$0")/../docker-compose.yml" ]]; then
    install_base_packages
    install_docker
    ensure_repository
    chmod +x scripts/install-server.sh scripts/repair-flyway-v37.sh 2>/dev/null || true
    exec "$(pwd)/scripts/install-server.sh" --in-repo --skip-docker "$@"
  fi

  if [[ -f "$(dirname "$0")/../docker-compose.yml" ]]; then
    cd "$(dirname "$0")/.."
  elif [[ -f docker-compose.yml ]]; then
    :
  else
    install_base_packages
    install_docker
    ensure_repository
  fi

  INSTALL_DIR="$(pwd)"
  log "Arbeitsverzeichnis: ${INSTALL_DIR}"

  if [[ "$SKIP_DOCKER" -eq 0 ]] && ! command -v docker >/dev/null 2>&1; then
    install_base_packages
    install_docker
  fi

  command -v docker >/dev/null 2>&1 || die "Docker fehlt — ohne --skip-docker erneut ausführen"

  ensure_env_file
  deploy_containers
  wait_for_mysql

  if ! repair_flyway_v37_if_needed; then
    warn "App antwortet noch nicht auf :8080"
    compose_cmd ps
    compose_cmd logs app --tail 40
    die "Installation unvollständig — Logs prüfen (ggf. RAM auf 4 GB erhöhen)"
  fi

  compose_cmd ps
  print_summary
}

main "$@"
