#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

echo "========================================"
echo "   Starting Backend and Frontend..."
echo "========================================"

# 检查 Java 是否可用且为 17
if ! command -v java >/dev/null 2>&1; then
	echo "[ERROR] Java not found. Please install JDK 17 and configure PATH."
	exit 1
fi

JAVA_VERSION_LINE=$(java -version 2>&1 | head -n 1)
JAVA_MAJOR=$(echo "$JAVA_VERSION_LINE" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')
if [ "$JAVA_MAJOR" != "17" ]; then
	echo "[ERROR] Detected Java major version: ${JAVA_MAJOR:-unknown}"
	echo "[ERROR] This project requires Java 17."
	exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
	echo "[ERROR] Maven not found. Please install Maven and configure PATH."
	echo "[HINT] After installation, verify with: mvn -version"
	exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
	echo "[ERROR] npm not found. Please install Node.js and configure PATH."
	echo "[HINT] After installation, verify with: npm -v"
	exit 1
fi

if [ ! -f "$SCRIPT_DIR/pom.xml" ]; then
	echo "[ERROR] Backend project file not found: $SCRIPT_DIR/pom.xml"
	exit 1
fi

if [ ! -f "$FRONTEND_DIR/package.json" ]; then
	echo "[ERROR] Frontend directory not found: $FRONTEND_DIR"
	exit 1
fi

echo "[INFO] Project directory: $SCRIPT_DIR"
echo "[INFO] Frontend directory: $FRONTEND_DIR"
echo "[INFO] Local development only requires MySQL by default."
echo "[INFO] Redis, RabbitMQ and Nacos are disabled locally unless APP_INFRA_*_ENABLED is set to true."

# 启动后端（后台运行，输出到终端）
cd "$SCRIPT_DIR" || exit
mvn spring-boot:run &
BACKEND_PID=$!

# 等待2秒
sleep 2

# 启动前端（后台运行，输出到终端）
cd "$FRONTEND_DIR" || exit
npm run dev &
FRONTEND_PID=$!

echo ""
echo "Both services are starting..."
echo "- Backend PID: $BACKEND_PID"
echo "- Frontend PID: $FRONTEND_PID"
echo ""
echo "Press Ctrl+C to stop both services"

# 等待用户中断
wait