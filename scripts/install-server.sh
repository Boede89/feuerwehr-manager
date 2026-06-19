#!/usr/bin/env bash
# Komplett-Installation auf frischem Debian-LXC (apt + Docker + App).
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

═══ Neuer Proxmox-LXC (ein Befehl, nur apt nötig) ═══

apt-get update && apt-get install -y git ca-certificates && git clone --depth 1 https://github.com/Boede89/feuerwehr-manager.git /opt/feuerwehr-manager && exec bash /opt/feuerwehr-manager/neuer-container --fresh

═══ Im bereits geklonten Repo ═══

  sudo ./neuer-container --fresh
  sudo ./scripts/install-server.sh --in-repo --fresh

Optionen:
  --fresh          MySQL-Volume löschen (leere DB, danach SQL-Import in der Web-UI)
  --dir PATH       Installationsverzeichnis (Standard: /opt/feuerwehr-manager)
  --skip-docker    Docker nicht installieren
  --use-cache      Docker-Build mit Cache
  -h, --help       Diese Hilfe
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
  log "Systempakete prüfen / installieren (git, curl, …)"
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
    log "Repository: ${INSTALL_DIR}"
    cd "${INSTALL_DIR}"
    if [[ -d .git ]]; then
      git pull --ff-only || warn "git pull fehlgeschlagen — fahre mit lokalem Stand fort"
    fi
    return 0
  fi

  log "Repository klonen nach ${INSTALL_DIR}"
  mkdir -p "$(dirname "${INSTALL_DIR}")"
  git clone --depth 1 --branch main "${REPO_URL}" "${INSTALL_DIR}"
  cd "${INSTALL_DIR}"
}

resolve_working_directory() {
  if [[ -f "$(dirname "$0")/../docker-compose.yml" ]]; then
    cd "$(dirname "$0")/.."
    return 0
  fi
  if [[ -f docker-compose.yml ]]; then
    return 0
  fi
  ensure_repository
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
  if compose_cmd ps app 2>/dev/null | grep -q 'Restarting'; then
    return 1
  fi
  compose_cmd ps app 2>/dev/null | grep -qE ' Up ' || return 1
  curl -fsS -o /dev/null "http://127.0.0.1:8080/login" 2>/dev/null
}

flyway_v37_needs_repair() {
  if compose_cmd logs app 2>&1 | grep -qiE 'version 37|atemschutz course|not resolved locally: 37'; then
    return 0
  fi
  compose_cmd exec mysql mysql -uff -pffsecret -N -e \
    "SELECT 1 FROM flyway_schema_history WHERE version='37' AND success=0 LIMIT 1" \
    feuerwehr_manager 2>/dev/null | grep -q 1
}

run_flyway_v37_repair() {
  log "Flyway V37 reparieren …"
  compose_cmd stop app 2>/dev/null || true
  compose_cmd exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-schema.sql \
    2>/dev/null || true
  compose_cmd exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-mark-success.sql
  compose_cmd up -d app
}

wait_for_app() {
  log "Warte auf App-Start …"
  local i
  for i in $(seq 1 36); do
    if app_is_up; then
      return 0
    fi
    if [[ "$i" -eq 6 ]] && flyway_v37_needs_repair; then
      run_flyway_v37_repair
    fi
    sleep 5
  done
  return 1
}

repair_flyway_v37_if_needed() {
  if ! flyway_v37_needs_repair; then
    return 1
  fi
  run_flyway_v37_repair
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
  install_base_packages
  resolve_working_directory
  INSTALL_DIR="$(pwd)"
  log "Arbeitsverzeichnis: ${INSTALL_DIR}"

  install_docker
  ensure_env_file
  deploy_containers
  wait_for_mysql

  if ! wait_for_app; then
    log "App noch nicht bereit — prüfe Flyway V37 …"
    if ! repair_flyway_v37_if_needed; then
      warn "App antwortet noch nicht auf :8080"
      compose_cmd ps
      compose_cmd logs app --tail 50
      die "Installation unvollständig — Logs prüfen (ggf. RAM auf 4 GB erhöhen)"
    fi
  fi

  compose_cmd ps
  print_summary
}

main "$@"
