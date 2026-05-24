#!/bin/bash

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

# 启动后端（后台运行，输出到终端）
cd E:\DWB\5.ETF\etfProject || exit
mvn spring-boot:run &
BACKEND_PID=$!

# 等待2秒
sleep 2

# 启动前端（后台运行，输出到终端）
cd E:\DWB\5.ETF\etfProject\frontend || exit
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