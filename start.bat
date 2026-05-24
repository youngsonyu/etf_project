@echo off
title ETF Project Launcher

echo ========================================
echo    Starting Backend and Frontend...
echo ========================================

:: 检查 Java 是否可用且为 17
where java >nul 2>nul
if errorlevel 1 (
	echo [ERROR] Java not found. Please install JDK 17 and configure PATH.
	pause
	exit /b 1
)

set "JAVA_MAJOR="
for /f "tokens=2 delims=\"" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
	for /f "tokens=1 delims=." %%m in ("%%v") do set "JAVA_MAJOR=%%m"
)
if not "%JAVA_MAJOR%"=="17" (
	echo [ERROR] Detected Java major version: %JAVA_MAJOR%
	echo [ERROR] This project requires Java 17.
	pause
	exit /b 1
)

:: 启动后端（在新的命令行窗口）
start "Backend" cmd /k "cd /d E:\DWB\5.ETF\etfProject && mvn spring-boot:run"

:: 等待2秒，确保后端开始启动
timeout /t 2 /nobreak >nul

:: 启动前端（在新的命令行窗口）
start "Frontend" cmd /k "cd /d E:\DWB\5.ETF\etfProject\frontend && npm run dev"

echo Both services are starting...
echo.
echo - Backend will run on: http://localhost:8080 (default Spring Boot port)
echo - Frontend will run on: http://localhost:5173 (default Vite port)
echo.
echo Close the terminal windows to stop the services.
pause