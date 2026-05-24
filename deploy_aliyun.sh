#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

print_header() {
  printf '\n==== %s ====%s\n' "$1" "$2"
}

check_command() {
  command -v "$1" >/dev/null 2>&1
}

install_docker() {
  if check_command docker; then
    echo "Docker already installed."
    return
  fi

  if check_command yum; then
    echo "Installing Docker with yum..."
    sudo yum install -y yum-utils
    sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
    sudo yum install -y docker-ce docker-ce-cli containerd.io
    sudo systemctl enable --now docker
  elif check_command apt-get; then
    echo "Installing Docker with apt-get..."
    sudo apt-get update
    sudo apt-get install -y ca-certificates curl gnupg lsb-release
    sudo mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/$(. /etc/os-release && echo "$ID")/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/$(. /etc/os-release && echo "$ID") $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io
    sudo systemctl enable --now docker
  else
    echo "[ERROR] No supported package manager found. Install Docker manually."
    exit 1
  fi
}

install_docker_compose() {
  if check_command docker && docker compose version >/dev/null 2>&1; then
    echo "Docker Compose plugin is available."
    return
  fi

  if check_command docker-compose; then
    echo "docker-compose binary is available."
    return
  fi

  if ! check_command curl; then
    echo "[ERROR] curl is required to install Docker Compose. Install curl first."
    exit 1
  fi

  echo "Installing Docker Compose..."
  sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
  sudo chmod +x /usr/local/bin/docker-compose
}

get_compose_cmd() {
  if check_command docker && docker compose version >/dev/null 2>&1; then
    echo "docker compose"
  elif check_command docker-compose; then
    echo "docker-compose"
  else
    echo ""
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

print_header "Aliyun ECS One-Click Deploy" ""

if [ ! -f docker-compose.yml ]; then
  echo "[ERROR] docker-compose.yml not found in $(pwd). Please run this script from the project root."
  exit 1
fi

print_header "Install or verify Docker" ""
install_docker
install_docker_compose

COMPOSE_CMD="$(get_compose_cmd)"
if [ -z "$COMPOSE_CMD" ]; then
  echo "[ERROR] Docker Compose is not available after installation."
  exit 1
fi

echo "Using compose command: $COMPOSE_CMD"

if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    cp .env.example .env
    echo "Created .env from .env.example. Review .env before deploying."
  else
    echo "[WARN] .env.example not found. Ensure environment variables are set in .env."
  fi
fi

print_header "Start Docker Compose Stack" ""
$COMPOSE_CMD up -d --build

print_header "Optional Data Import" ""
load_env
SQL_FILE=""
if [ -f "$REPO_ROOT/etf_db_dump.sql" ]; then
  SQL_FILE="$REPO_ROOT/etf_db_dump.sql"
elif [ -f "$REPO_ROOT/init.sql" ]; then
  SQL_FILE="$REPO_ROOT/init.sql"
fi

if [ -n "$SQL_FILE" ]; then
  if [ -z "${MYSQL_ROOT_PASSWORD:-}" ] || [ -z "${MYSQL_DATABASE:-}" ]; then
    echo "[WARN] MYSQL_ROOT_PASSWORD or MYSQL_DATABASE is not set in .env. Skipping import."
  else
    MYSQL_CONTAINER="$(docker ps --filter "name=etf-mysql" --format '{{.Names}}' | head -n1)"
    if [ -z "$MYSQL_CONTAINER" ]; then
      MYSQL_CONTAINER="$(docker ps --filter "name=mysql" --format '{{.Names}}' | head -n1)"
    fi
    if [ -z "$MYSQL_CONTAINER" ]; then
      echo "[WARN] Cannot find MySQL container by name. Skipping SQL import."
    else
      echo "Found MySQL container: $MYSQL_CONTAINER"
      docker cp "$SQL_FILE" "$MYSQL_CONTAINER":/tmp/init.sql
      docker exec -i "$MYSQL_CONTAINER" sh -c "mysql -u root -p\"$MYSQL_ROOT_PASSWORD\" \"$MYSQL_DATABASE\" < /tmp/init.sql"
      echo "SQL import completed from $(basename "$SQL_FILE")."
    fi
  fi
else
  echo "No SQL init file found at $REPO_ROOT/etf_db_dump.sql or $REPO_ROOT/init.sql. If you want to import data, add one of these files and rerun the script."
fi

print_header "Health Check" ""
for i in $(seq 1 15); do
  if curl -sf http://127.0.0.1:8080/api/infra/health >/dev/null 2>&1; then
    echo "Backend health check passed."
    break
  fi
  echo "Waiting for backend health check... ($i/15)"
  sleep 3
  if [ "$i" -eq 15 ]; then
    echo "[WARN] Backend did not become healthy in time. Check Docker logs with: $COMPOSE_CMD logs backend"
    exit 1
  fi
done

print_header "Deployment Completed" ""
echo "Backend is available at http://127.0.0.1:8080"
echo "If you need to expose this externally, configure ECS security group and server firewall."