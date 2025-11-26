# Script: create_pr.ps1
# Crea un Pull Request en GitHub para la rama actual hacia la rama `manual-imagenes-dragdrop`.
# Requiere un token con scope `repo`. Ponerlo en la variable de entorno GITHUB_TOKEN o te pedirá uno.

param(
    [string]$base = "manual-imagenes-dragdrop",
    [string]$title = "backup: añadir filtro VPN y respetar selección por filtro",
    [string]$body = "Respaldo: cambios en `ControladorPrincipal.java` para agregar filtro VPN y que el header checkbox respete filtros."
)

# Obtener token del entorno o pedirlo
$token = $env:GITHUB_TOKEN
if (-not $token) {
    Write-Host "No se encontró la variable de entorno GITHUB_TOKEN." -ForegroundColor Yellow
    $token = Read-Host -Prompt "Introduce tu GitHub Personal Access Token (se mostrará en claro)"
}

if (-not $token) {
    Write-Error "No se proporcionó token. Abortando."
    exit 1
}

# Obtener branch actual
$branch = git rev-parse --abbrev-ref HEAD
if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo obtener la rama actual."; exit 1 }
$branch = $branch.Trim()

# Obtener remoto origin y parsear owner/repo
$remoteUrl = git remote get-url origin
if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo obtener URL del remoto origin."; exit 1 }
$remoteUrl = $remoteUrl.Trim()

# Soporta SSH y HTTPS
if ($remoteUrl -match 'git@github.com:(.*)\/(.*)\.git') {
    $owner = $matches[1]
    $repo = $matches[2]
} elseif ($remoteUrl -match 'https?://github.com\/(.*)\/(.*)\.git') {
    $owner = $matches[1]
    $repo = $matches[2]
} else {
    Write-Error "No se pudo parsear owner/repo desde: $remoteUrl"; exit 1
}

# Preparar payload
$payload = @{ title = $title; head = $branch; base = $base; body = $body }
$json = $payload | ConvertTo-Json -Depth 6

# Llamada API
$uri = "https://api.github.com/repos/$owner/$repo/pulls"
$headers = @{ Authorization = "token $token"; Accept = "application/vnd.github.v3+json"; "User-Agent" = "OrquestadorScript" }

try {
    $resp = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -Body $json -ContentType 'application/json'
    if ($resp.html_url) {
        Write-Host "PR creado: $($resp.html_url)" -ForegroundColor Green
        exit 0
    } else {
        Write-Error "Respuesta inesperada de la API: $($resp | ConvertTo-Json)"
        exit 1
    }
} catch {
    Write-Error "Error creando PR: $($_.Exception.Message)"
    if ($_.Exception.Response -ne $null) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $text = $reader.ReadToEnd()
        Write-Error "Detalles: $text"
    }
    exit 1
}
