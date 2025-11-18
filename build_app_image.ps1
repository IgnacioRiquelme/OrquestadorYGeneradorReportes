$ErrorActionPreference = 'Stop'
$dest = 'C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes'
if (Test-Path $dest) {
    Write-Host "Eliminando carpeta existente: $dest"
    Remove-Item -LiteralPath $dest -Recurse -Force
} else {
    Write-Host "No existía carpeta previa: $dest"
}
Write-Host 'Ejecutando jpackage para crear app-image...'
jpackage --type app-image `
  --name OrquestadorYGeneradorReportes `
  --input target `
  --main-jar orquestador-automatizaciones-1.0-SNAPSHOT-all.jar `
  --main-class com.orquestador.app.Main `
  --app-version 1.0 `
  --dest 'C:\Users\IARC\Desktop' `
  --verbose
if ($LASTEXITCODE -ne 0) { Write-Error "jpackage finalizó con código $LASTEXITCODE"; exit $LASTEXITCODE } 
Write-Host 'jpackage finalizó correctamente.'
