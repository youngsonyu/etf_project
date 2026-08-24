@echo off
title ETF Project Launcher

set "PROJECT_DIR=%~dp0"
set "FRONTEND_DIR=%PROJECT_DIR%\frontend"

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
set "JAVA_VERSION="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
	set "JAVA_VERSION=%%v"
)
set "JAVA_VERSION=%JAVA_VERSION:"=%"
for /f "tokens=1 delims=." %%m in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%m"
if not "%JAVA_MAJOR%"=="17" (
	echo [ERROR] Detected Java major version: %JAVA_MAJOR%
	echo [ERROR] This project requires Java 17.
	pause
	exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
	echo [ERROR] Maven not found. Please install Maven and configure PATH.
	echo [HINT] After installation, verify with: mvn -version
	pause
	exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
	echo [ERROR] npm not found. Please install Node.js and configure PATH.
	echo [HINT] After installation, verify with: npm -v
	pause
	exit /b 1
)

if not exist "%PROJECT_DIR%\pom.xml" (
	echo [ERROR] Backend project file not found: %PROJECT_DIR%\pom.xml
	pause
	exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
	echo [ERROR] Frontend directory not found: %FRONTEND_DIR%
	pause
	exit /b 1
)

echo [INFO] Project directory: %PROJECT_DIR%
echo [INFO] Frontend directory: %FRONTEND_DIR%
echo [INFO] Local development only requires MySQL by default.
echo [INFO] Redis, RabbitMQ and Nacos are disabled locally unless APP_INFRA_*_ENABLED is set to true.
echo.

:: 启动后端（在新的命令行窗口）
start "Backend" /d "%PROJECT_DIR%" cmd /k "mvn spring-boot:run"

:: 等待2秒，确保后端开始启动
timeout /t 2 /nobreak >nul

:: 启动前端（在新的命令行窗口）
start "Frontend" /d "%FRONTEND_DIR%" cmd /k "if exist node_modules\.bin\vite.cmd (npm run dev) else (echo [INFO] Frontend dependencies missing, running npm install... && npm install && npm run dev)"

echo Both services are starting...
echo.
echo - Backend will run on: http://localhost:8080 (default Spring Boot port)
echo - Frontend will run on: http://localhost:5173 (default Vite port)
echo.
echo Close the terminal windows to stop the services.
pause