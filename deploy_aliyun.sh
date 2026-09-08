#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

APP_NAME="etf-backend"
RUN_DIR="$REPO_ROOT/.run"
PID_FILE="$RUN_DIR/${APP_NAME}.pid"
LOG_FILE="$RUN_DIR/${APP_NAME}.log"
JAR_PATH="$REPO_ROOT/target/etf-backend-1.0.0.jar"

print_header() {
  printf '\n==== %s ====\n' "$1"
}

check_command() {
  command -v "$1" >/dev/null 2>&1
}

require_command() {
  if ! check_command "$1"; then
    echo "[ERROR] Missing required command: $1"
    exit 1
  fi
}

load_env() {
  if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
  fi
}

check_java_version() {
  local version_line major
  version_line="$(java -version 2>&1 | head -n1)"
  major="$(echo "$version_line" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')"
  if [ "$major" != "17" ]; then
    echo "[ERROR] Java 17 is required, detected: ${major:-unknown}"
    exit 1
  fi
}

stop_existing_process() {
  if [ ! -f "$PID_FILE" ]; then
    return
  fi
  local old_pid
  old_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -n "$old_pid" ] && kill -0 "$old_pid" >/dev/null 2>&1; then
    echo "Stopping existing process (PID=$old_pid)..."
    kill "$old_pid"
    for _ in $(seq 1 20); do
      if ! kill -0 "$old_pid" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    if kill -0 "$old_pid" >/dev/null 2>&1; then
      echo "Process still running, sending SIGKILL..."
      kill -9 "$old_pid"
    fi
  fi
  rm -f "$PID_FILE"
}

check_mysql_connectivity() {
  local host port user db
  host="${MYSQL_HOST:-127.0.0.1}"
  port="${MYSQL_PORT:-3306}"
  user="${MYSQL_USER:-root}"
  db="${MYSQL_DB:-amazingdata_etf}"

  if check_command mysql; then
    if mysql -h "$host" -P "$port" -u "$user" -p"${MYSQL_PASSWORD:-}" -e "SELECT 1" "$db" >/dev/null 2>&1; then
      echo "MySQL connectivity check passed: ${user}@${host}:${port}/${db}"
      return
    fi
    echo "[WARN] MySQL connectivity check failed. Verify MYSQL_* values in .env."
    return
  fi

  echo "[WARN] mysql client not found, skipping active MySQL connectivity check."
}

print_header "Aliyun ECS Java + MySQL Deploy"

if [ ! -f pom.xml ]; then
  echo "[ERROR] pom.xml not found in $(pwd). Run this script from project root."
  exit 1
fi

if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    cp .env.example .env
    echo "Created .env from .env.example. Review credentials before deploying."
  else
    echo "[WARN] .env.example not found. Ensure MYSQL_* env vars are configured."
  fi
fi

load_env

print_header "Pre-check"
require_command java
require_command mvn
require_command curl
check_java_version
check_mysql_connectivity

print_header "Build backend"
mvn -q -DskipTests package

if [ ! -f "$JAR_PATH" ]; then
  echo "[ERROR] Build output not found: $JAR_PATH"
  exit 1
fi

mkdir -p "$RUN_DIR"
stop_existing_process

JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8}"
SERVER_PORT="${SERVER_PORT:-8080}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-amazingdata_etf}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
APP_INFRA_REDIS_ENABLED="${APP_INFRA_REDIS_ENABLED:-false}"
APP_INFRA_RABBITMQ_ENABLED="${APP_INFRA_RABBITMQ_ENABLED:-false}"
APP_INFRA_NACOS_ENABLED="${APP_INFRA_NACOS_ENABLED:-false}"

print_header "Start backend"
nohup env \
  SERVER_PORT="$SERVER_PORT" \
  MYSQL_HOST="$MYSQL_HOST" \
  MYSQL_PORT="$MYSQL_PORT" \
  MYSQL_DB="$MYSQL_DB" \
  MYSQL_USER="$MYSQL_USER" \
  MYSQL_PASSWORD="$MYSQL_PASSWORD" \
  APP_INFRA_REDIS_ENABLED="$APP_INFRA_REDIS_ENABLED" \
  APP_INFRA_RABBITMQ_ENABLED="$APP_INFRA_RABBITMQ_ENABLED" \
  APP_INFRA_NACOS_ENABLED="$APP_INFRA_NACOS_ENABLED" \
  java $JAVA_OPTS -jar "$JAR_PATH" >"$LOG_FILE" 2>&1 &

NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"
echo "Backend started, PID=$NEW_PID"
echo "Log file: $LOG_FILE"

print_header "Health check"
for i in $(seq 1 20); do
  if curl -sf "http://127.0.0.1:${SERVER_PORT}/api/infra/health" >/dev/null 2>&1; then
    echo "Backend health check passed."
    break
  fi
  sleep 2
  if [ "$i" -eq 20 ]; then
    echo "[ERROR] Backend did not become healthy in time."
    echo "Recent logs:"
    tail -n 60 "$LOG_FILE" || true
    exit 1
  fi
done

print_header "Deployment completed"
echo "Backend API: http://127.0.0.1:${SERVER_PORT}"
echo "API docs:    http://127.0.0.1:${SERVER_PORT}/doc.html"
echo "Stop command: kill \$(cat $PID_FILE)"
