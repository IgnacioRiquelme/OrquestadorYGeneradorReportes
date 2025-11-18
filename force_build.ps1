$ErrorActionPreference='Stop'
$dest='C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes'
$zip = "$env:TEMP\handle.zip"
$exe = "$env:TEMP\handle.exe"
if (-not (Test-Path $exe)) {
    Write-Host "Descargando Handle (Sysinternals)..."
    Invoke-WebRequest -Uri 'https://download.sysinternals.com/files/Handle.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $env:TEMP -Force
}
Write-Host "Ejecutando handle.exe para buscar locks..."
$hOut = & $exe -accepteula OrquestadorYGeneradorReportes 2>&1
$hOut | ForEach-Object { Write-Host $_ }
$pids = @()
$matches = $hOut | Select-String -Pattern 'pid: (\d+)' -AllMatches
foreach ($m in $matches) {
    foreach ($mm in $m.Matches) { $pids += $mm.Groups[1].Value }
}
$pids = $pids | Select-Object -Unique
if ($pids.Count -eq 0) {
    Write-Host 'No PIDs encontrados que bloqueen la carpeta.'
} else {
    Write-Host "PIDs encontrados: $($pids -join ', ')"
    foreach ($pidValue in $pids) {
        try {
            # Evitar matar el proceso actual del script y el Explorador/VSCode para no cerrar el entorno
            if ([int]$pidValue -eq $PID) { Write-Host "Saltando PID actual del script: $pidValue"; continue }
            $proc = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
            if ($proc -and ($proc.ProcessName -in @('explorer','Code'))) { Write-Host "Saltando proceso interactivo: $($proc.ProcessName) PID $pidValue"; continue }
            Write-Host "Matando PID $pidValue..."
            Stop-Process -Id ([int]$pidValue) -Force -ErrorAction Stop
            Write-Host "PID $pidValue terminado."
        } catch {
            Write-Warning ("No se pudo terminar PID " + $pidValue + ": " + $_)
        }
    }
}
Write-Host 'Intentando eliminar la carpeta...'
Remove-Item -LiteralPath $dest -Recurse -Force -ErrorAction Stop
Write-Host 'Carpeta eliminada.'
Write-Host 'Ejecutando jpackage para crear app-image...'
jpackage --type app-image --name OrquestadorYGeneradorReportes --input target --main-jar orquestador-automatizaciones-1.0-SNAPSHOT-all.jar --main-class com.orquestador.app.Main --app-version 1.0 --dest 'C:\Users\IARC\Desktop' --verbose
Write-Host 'jpackage finalizó.'
