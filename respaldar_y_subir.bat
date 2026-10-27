@echo off
REM Script para crear respaldo limpio y subirlo a GitHub automáticamente

REM 1. Cambia a la ruta del proyecto original
cd /d "C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes\OrquestadorYGeneradorReportes"

REM 2. Obtén el hash del commit de ayer (ajusta si es necesario)
git log --since="2 days ago" --oneline > commits.txt
set /p COMMIT_ID=<commits.txt
set COMMIT_ID=%COMMIT_ID:~0,7%

REM 3. Crea carpeta temporal para el respaldo
cd ..
set BACKUP_DIR=OrquestadorYGeneradorReportes-Backup
rmdir /s /q %BACKUP_DIR%
mkdir %BACKUP_DIR%

REM 4. Extrae el estado exacto del commit funcional
cd OrquestadorYGeneradorReportes

git archive %COMMIT_ID% | tar -x -C "..\%BACKUP_DIR%"

REM 5. Inicializa nuevo repo en la carpeta de respaldo
cd ..\%BACKUP_DIR%
git init

REM 6. Crea .gitignore estándar para Java
echo target/ > .gitignore
echo *.class >> .gitignore
echo *.log >> .gitignore
echo .idea/ >> .gitignore
echo *.iml >> .gitignore
echo .DS_Store >> .gitignore

REM 7. Primer commit
git add .
git commit -m "Respaldo limpio del estado funcional"

REM 8. Conecta y sube al nuevo repo
set REPO_URL=https://github.com/IgnacioRiquelme/OrquestadorFullRespaldo.git
git remote add origin %REPO_URL%
git branch -M main
git push -u origin main

REM 9. Limpieza
del commits.txt

echo Respaldo subido correctamente.
pause
