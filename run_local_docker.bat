@echo off
echo ===================================================
echo   TranquiApp - Iniciando Entorno Local con Docker
echo ===================================================
echo.
echo Verificando si Docker esta ejecutandose...
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo Docker no esta activo. Iniciando Docker Desktop...
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    echo Esperando a que Docker se inicialice (30 segundos)...
    timeout /t 30 /nobreak
)

:check
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo Esperando 10 segundos mas para que Docker responda...
    timeout /t 10 /nobreak
    goto check
)

echo.
echo Docker esta activo! Iniciando contenedores...
docker compose up --build -d
echo.
echo ===================================================
echo   TranquiApp se inicio correctamente!
echo   Frontend: http://localhost:3000
echo   Backend: http://localhost:8081
echo ===================================================
pause
