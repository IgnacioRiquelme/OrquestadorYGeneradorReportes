$desktop = [Environment]::GetFolderPath('Desktop')
$lnkPath = Join-Path $desktop 'OrquestadorYGeneradorReportes (EXE).lnk'
## jpackage creó una subcarpeta con el nombre de la app; ajustar ruta al exe dentro de esa carpeta
$target = 'C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes\OrquestadorYGeneradorReportes\OrquestadorYGeneradorReportes.exe'
$work = 'C:\Users\IARC\Desktop\OrquestadorYGeneradorReportes\OrquestadorYGeneradorReportes'
if (-Not (Test-Path $target)) { Write-Error "Target not found: $target"; exit 2 }
$wsh = New-Object -ComObject WScript.Shell
$sc = $wsh.CreateShortcut($lnkPath)
$sc.TargetPath = $target
$sc.WorkingDirectory = $work
$sc.IconLocation = "$target,0"
$sc.Save()
Write-Output "Shortcut created: $lnkPath"
