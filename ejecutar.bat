@echo off
setlocal
cd /d "%~dp0"

if exist "C:\apache-maven-3.9.11\bin\mvn.cmd" (
    set "MVN_CMD=C:\apache-maven-3.9.11\bin\mvn.cmd"
) else (
    set "MVN_CMD=mvn"
)

call "%MVN_CMD%" javafx:run > "%~dp0app_run.log" 2>&1
set "MVN_RESULT=%errorlevel%"

if not "%MVN_RESULT%"=="0" (
    start "Error al iniciar Orquestador de Automatizaciones" cmd /k "type "%~dp0app_run.log" & echo. & echo ============================================ & echo  OCURRIO UN ERROR AL INICIAR LA APLICACION & echo ============================================ & echo. & pause"
)

endlocal
