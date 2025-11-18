${desktop} = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop 'OrquestadorYGeneradorReportes.lnk'
if (-Not (Test-Path $lnk)) {
    Write-Error "No se encontró $lnk"
    exit 2
}
$sc = (New-Object -ComObject WScript.Shell).CreateShortcut($lnk)
Write-Output "Target: $($sc.TargetPath)"
Write-Output "Arguments: $($sc.Arguments)"
Write-Output "WorkingDirectory: $($sc.WorkingDirectory)"
Write-Output "Icon: $($sc.IconLocation)"
