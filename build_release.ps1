# =============================================================================
# build_release.ps1 - Genera el instalador de OrquestadorAutomatizaciones
# =============================================================================
# Requisitos: JDK 21, Maven, Inno Setup 6
# Salida: dist\OrquestadorAutomatizaciones-Setup.exe
# =============================================================================

$ErrorActionPreference = 'Stop'
$ROOT         = $PSScriptRoot
$TARGET       = "$ROOT\target"
$DIST         = "C:\orq_dist"
$JAR          = "orquestador-automatizaciones-1.1.0-all.jar"
$ICON         = "$ROOT\assets\irc_solutions.ico"
$ISCC         = @("C:\Program Files (x86)\Inno Setup 6\ISCC.exe", "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $ISCC) { throw "No se encontro Inno Setup 6 (ISCC.exe)." }
$JDK_HOME_BUILD = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\jmods")) { $env:JAVA_HOME } else { 'C:\Program Files\Java\jdk-21' }
$JFXJMODS     = "$ROOT\tools\javafx-jmods"
$APP_IMAGE_DIR = "$DIST\OrquestadorAutomatizaciones"

Write-Host ""
Write-Host "============================================================"
Write-Host "  BUILD RELEASE - Orquestador de Automatizaciones"
Write-Host "============================================================"
Write-Host ""

# ── Paso 1: Compilar y empaquetar con Maven ────────────────────────────────
Write-Host "[1/4] Compilando y ofuscando proyecto con Maven + ProGuard..."
Set-Location $ROOT
$mvnResult = & mvn clean verify -q -B 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host $mvnResult
    throw "Maven verify falló (codigo $LASTEXITCODE). Revisa los errores arriba."
}
if (-not (Test-Path "$TARGET\$JAR")) {
    throw "No se encontró el JAR: $TARGET\$JAR"
}
$jarSize = [math]::Round((Get-Item "$TARGET\$JAR").Length / 1MB, 1)
Write-Host "    => JAR generado: $JAR ($jarSize MB)" -ForegroundColor Green

# ── Paso 2: Limpiar directorio de distribución ────────────────────────────
Write-Host ""
Write-Host "[2/4] Preparando directorio dist..."
if (Test-Path $DIST) {
    # Usar cmd /c rmdir que maneja archivos bloqueados y de solo lectura
    cmd /c "rmdir /s /q `"$DIST`""
    Start-Sleep -Milliseconds 500
    if (Test-Path $DIST) {
        throw "No se pudo eliminar la carpeta dist. Cierra el Explorador si tienes esa carpeta abierta e intenta de nuevo."
    }
}
New-Item -ItemType Directory -Path $DIST | Out-Null
# ── Verificar/descargar JavaFX jmods ──────────────────────────────────────
Write-Host ""
Write-Host "[2.5/4] Verificando JavaFX jmods (necesarios para DLLs nativas)..."
if (-not (Test-Path "$JFXJMODS\javafx.graphics.jmod")) {
    Write-Host "      Descargando JavaFX 21.0.2 jmods para Windows (~20 MB)..."
    $jmodsZip = "$ROOT\tools\javafx-jmods.zip"
    $url = "https://download2.gluonhq.com/openjfx/21.0.2/openjfx-21.0.2_windows-x64_bin-jmods.zip"
    Invoke-WebRequest -Uri $url -OutFile $jmodsZip -UseBasicParsing
    Expand-Archive -Path $jmodsZip -DestinationPath "$ROOT\tools" -Force
    $extracted = Get-ChildItem "$ROOT\tools" -Directory | Where-Object { $_.Name -like "*jmods*" -or $_.Name -like "*openjfx*" } | Select-Object -First 1
    if ($extracted.FullName -ne $JFXJMODS) {
        Rename-Item $extracted.FullName $JFXJMODS -ErrorAction SilentlyContinue
    }
    Remove-Item $jmodsZip -Force -ErrorAction SilentlyContinue
    Write-Host "      => jmods descargados correctamente" -ForegroundColor Green
} else {
    Write-Host "      => jmods ya disponibles: $JFXJMODS" -ForegroundColor Green
}
# ── Paso 2.5: Preparar staging con solo el JAR principal ─────────────────
$STAGING = "$DIST\staging"
New-Item -ItemType Directory -Path $STAGING | Out-Null
Copy-Item "$TARGET\$JAR" -Destination $STAGING
Write-Host "    => Staging listo con: $JAR" -ForegroundColor Green

# ── Paso 3: jpackage → app-image ──────────────────────────────────────────
Write-Host ""
Write-Host "[3/4] Generando app-image con jpackage (JRE embebido)..."
Write-Host "      Esto puede tardar 1-2 minutos..."

$jpackageArgs = @(
    "--type", "app-image",
    "--name", "OrquestadorAutomatizaciones",
    "--input", $STAGING,
    "--main-jar", $JAR,
    "--main-class", "com.orquestador.app.Main",
    "--app-version", "1.0",
    "--dest", $DIST,
    "--vendor", "Orquestador",
    "--description", "Orquestador de Automatizaciones",
    "--module-path", "$JFXJMODS;$((Get-Item "$JDK_HOME_BUILD\jmods").FullName)",
    "--add-modules", "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.swing",
    "--java-options", "-Xmx512m",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "-Djava.awt.headless=false",
    "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.nio=ALL-UNNAMED"
)

if (Test-Path $ICON) {
    $jpackageArgs += "--icon", $ICON
}

& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage falló con código $LASTEXITCODE"
}

if (-not (Test-Path "$APP_IMAGE_DIR\OrquestadorAutomatizaciones.exe")) {
    throw "No se generó el exe esperado en $APP_IMAGE_DIR"
}
Write-Host "    => App-image generado en: $APP_IMAGE_DIR" -ForegroundColor Green

New-Item -ItemType Directory -Path "$APP_IMAGE_DIR\Esqueleto" -Force | Out-Null
Copy-Item "$ROOT\Esqueleto\Esqueleto Manuales BCI.docx" -Destination "$APP_IMAGE_DIR\Esqueleto" -Force

# ── Paso 3.5: Empaquetar herramientas (JDK, Maven, Node.js, Newman) ───────
Write-Host ""
Write-Host "[3.5/5] Empaquetando herramientas de ejecucion de automatizaciones..."

$JDK_SRC  = if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) { $env:JAVA_HOME } else { Split-Path (Get-Command java -ErrorAction Stop).Source -Parent | Split-Path }
$MVN_SRC  = Split-Path (Get-Command mvn  -ErrorAction Stop).Source -Parent | Split-Path
$NODE_SRC = Split-Path (Get-Command node -ErrorAction Stop).Source -Parent

$JDK_DEST  = "$APP_IMAGE_DIR\tools\jdk"
$MVN_DEST  = "$APP_IMAGE_DIR\tools\maven"
$NODE_DEST = "$APP_IMAGE_DIR\tools\nodejs"

Write-Host "      JDK  : $JDK_SRC"
Write-Host "      Maven: $MVN_SRC"
Write-Host "      Node : $NODE_SRC"
New-Item -ItemType Directory -Path "$APP_IMAGE_DIR\tools" -Force | Out-Null

Write-Host "      Copiando JDK (puede tardar unos segundos)..."
Copy-Item $JDK_SRC  -Destination $JDK_DEST  -Recurse -Force
Write-Host "      => JDK copiado" -ForegroundColor Green

Write-Host "      Copiando Maven..."
Copy-Item $MVN_SRC  -Destination $MVN_DEST  -Recurse -Force
Write-Host "      => Maven copiado" -ForegroundColor Green

Write-Host "      Copiando Node.js..."
Copy-Item $NODE_SRC -Destination $NODE_DEST -Recurse -Force
Write-Host "      => Node.js copiado" -ForegroundColor Green

Write-Host "      Pre-instalando Newman (colecciones Postman)..."
try {
    $ErrorActionPreference = 'Continue'
    & "$NODE_DEST\npm.cmd" install -g newman --prefix "$NODE_DEST" 2>&1 | Out-Null
    $ErrorActionPreference = 'Stop'
    Write-Host "      => Newman instalado" -ForegroundColor Green
} catch {
    $ErrorActionPreference = 'Stop'
    Write-Host "      ADVERTENCIA: Newman no se pudo preinstalar (no critico)" -ForegroundColor Yellow
}

# ── Paso 3.6: Bundlear repositorio Maven local (dependencias offline) ────
Write-Host "      Copiando repositorio Maven local para ejecucion offline..."
$M2_REPO      = "$env:USERPROFILE\.m2\repository"
$MVN_USER_DIR = "$APP_IMAGE_DIR\tools\maven-user"
$MVN_REPO_DEST = "$MVN_USER_DIR\repository"
if (Test-Path $M2_REPO) {
    New-Item -ItemType Directory -Path $MVN_USER_DIR -Force | Out-Null
    & robocopy $M2_REPO $MVN_REPO_DEST /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy del repositorio Maven fallo (codigo $LASTEXITCODE)" }
    $repoSize = [math]::Round((Get-ChildItem $MVN_REPO_DEST -Recurse -File | Measure-Object Length -Sum).Sum / 1MB, 0)
    Write-Host "      => Repositorio Maven bundleado ($repoSize MB) - proyectos corren offline" -ForegroundColor Green
} else {
    Write-Host "      ADVERTENCIA: No se encontro .m2\repository. Los proyectos necesitaran internet." -ForegroundColor Yellow
}

$toolsSize = [math]::Round((Get-ChildItem "$APP_IMAGE_DIR\tools" -Recurse -File | Measure-Object Length -Sum).Sum / 1MB, 0)
Write-Host "    => Herramientas empaquetadas ($toolsSize MB)" -ForegroundColor Green

# ── Paso 3.7: Template de proyectos con rutas parametrizadas ──────────────
Write-Host ""
Write-Host "[3.7/5] Generando template de proyectos para wizard del instalador..."
$m2f = "$env:LOCALAPPDATA\OrquestadorAutomatizaciones\proyectos.json"
if (Test-Path $m2f) {
    $rawJson = Get-Content $m2f -Raw -Encoding UTF8
    $templateJson = $rawJson `
        -replace [regex]::Escape('C:\\Automatizaciones_V2\\'), '__PROYECTOS__\\' `
        -replace [regex]::Escape('C:\\Users\\IARC\\Desktop\\Entregas Documentos Parchado\\'), '__INFORMES__\\' `
        -replace [regex]::Escape('C:\\Users\\IARC\\Desktop\\Nuevos esqueletos\\'), '__ESQUELETOS__\\'
    $tObj = $templateJson | ConvertFrom-Json
    # Filtrar proyectos deshabilitados/incompletos (sin campo ultimaEjecucion)
    $tObj = @($tObj | Where-Object { $_.PSObject.Properties['ultimaEjecucion'] })
    foreach ($p in $tObj) {
        if ($p.PSObject.Properties['estado'])          { $p.estado           = "PENDIENTE" }
        if ($p.PSObject.Properties['ultimaEjecucion']) { $p.ultimaEjecucion  = $null }
        if ($p.PSObject.Properties['mensajeError'])    { $p.mensajeError     = $null }
        if ($p.PSObject.Properties['rutaLogEjecucion']){ $p.rutaLogEjecucion = $null }
        if ($p.PSObject.Properties['seleccionado'])    { $p.seleccionado     = $false }
        if ($p.PSObject.Properties['intentoActual'])   { $p.intentoActual    = 0 }
    }
    $finalTemplate = $tObj | ConvertTo-Json -Depth 20
    [System.IO.File]::WriteAllText("$APP_IMAGE_DIR\proyectos_template.json", $finalTemplate, [System.Text.Encoding]::UTF8)
    Write-Host "    => Template generado: $($tObj.Count) proyectos" -ForegroundColor Green
} else {
    Write-Host "    ADVERTENCIA: No se encontro proyectos.json local. El wizard instalara configuracion vacia." -ForegroundColor Yellow
    '[  ]' | Set-Content "$APP_IMAGE_DIR\proyectos_template.json" -Encoding UTF8
}

# Script PowerShell embebido en la app que ejecuta el wizard post-install
$setupConfigScript = @'
param(
    [string]$Proyectos,
    [string]$Informes,
    [string]$Esqueletos,
    [string]$TemplateFile,
    [string]$ConfigDir
)
New-Item -ItemType Directory -Force -Path $ConfigDir | Out-Null
$dest = Join-Path $ConfigDir 'proyectos.json'
if (Test-Path $dest) {
    # Ya existe una configuración del usuario: respetarla.
    # No se sobrescribe para conservar marcas (requiereAyuda), rutas y estados.
    exit 0
}
$pj = $Proyectos -replace '\\', '\\'
$ij = $Informes  -replace '\\', '\\'
$ej = $Esqueletos -replace '\\', '\\'
$t = [System.IO.File]::ReadAllText($TemplateFile, [System.Text.Encoding]::UTF8)
$t = $t.Replace('__PROYECTOS__', $pj)
$t = $t.Replace('__INFORMES__', $ij)
$t = $t.Replace('__ESQUELETOS__', $ej)
[System.IO.File]::WriteAllText($dest, $t, [System.Text.Encoding]::UTF8)
'@
[System.IO.File]::WriteAllText("$APP_IMAGE_DIR\setup_config.ps1", $setupConfigScript, [System.Text.Encoding]::UTF8)
Write-Host "    => setup_config.ps1 generado" -ForegroundColor Green

# ── Paso 4: Inno Setup → instalador .exe ──────────────────────────────────
Write-Host ""
Write-Host "[5/5] Creando instalador con Inno Setup 6..."

$issScript = @"
; ============================================================
; Inno Setup Script - OrquestadorAutomatizaciones
; Generado por build_release.ps1
; ============================================================

#define MyAppName "Orquestador de Automatizaciones"
#define MyAppExeName "OrquestadorAutomatizaciones.exe"
#define MyAppVersion "1.0"
#define MyAppPublisher "Orquestador"
#define MyAppSourceDir "$($APP_IMAGE_DIR.Replace('\','\\'))"
#define MyOutputDir "$($DIST.Replace('\','\\'))"

[Setup]
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\OrquestadorAutomatizaciones
DefaultGroupName={#MyAppName}
OutputDir={#MyOutputDir}
OutputBaseFilename=OrquestadorAutomatizaciones-Setup
Compression=lzma2/ultra64
SolidCompression=yes
PrivilegesRequired=lowest
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#MyAppExeName}
SetupIconFile=$ICON
WizardStyle=modern

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear icono en el Escritorio"; GroupDescription: "Accesos directos:"; Flags: checkedonce
Name: "startmenuicon"; Description: "Crear entrada en Menu Inicio"; GroupDescription: "Accesos directos:"; Flags: checkedonce

[Files]
Source: "{#MyAppSourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Registry]
; Variables de entorno del usuario (sin necesitar admin)
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; ValueName: "JAVA_HOME"; ValueData: "{app}\\tools\\jdk"; Flags: uninsdeletevalue
Root: HKCU; Subkey: "Environment"; ValueType: expandsz; ValueName: "MAVEN_HOME"; ValueData: "{app}\\tools\\maven"; Flags: uninsdeletevalue

[Icons]
Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartmenu}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startmenuicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Iniciar {#MyAppName}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}"

[Code]
var
  PageProyectos: TInputDirWizardPage;
  PageInformes:  TInputDirWizardPage;
  PageEsquelos:  TInputDirWizardPage;

procedure InitializeWizard;
begin
  PageProyectos := CreateInputDirPage(wpSelectDir,
    'Carpeta de proyectos',
    'Donde estan las carpetas con las automatizaciones',
    'Selecciona la carpeta raiz que contiene los proyectos Maven/Newman:',
    False, '');
  PageProyectos.Add('');
  PageProyectos.Values[0] := 'C:\Proyectos';

  PageInformes := CreateInputDirPage(PageProyectos.ID,
    'Carpeta de informes',
    'Donde se guardaran los informes Word y PDF generados',
    'Selecciona la carpeta de Informes (debe tener subcarpetas WORD y PDF):',
    False, '');
  PageInformes.Add('');
  PageInformes.Values[0] := ExpandConstant('{userdesktop}\Informes');

  PageEsquelos := CreateInputDirPage(PageInformes.ID,
    'Carpeta de templates Word',
    'Donde estan los documentos Word base para generar los informes',
    'Selecciona la carpeta de esqueletos/templates Word:',
    False, '');
  PageEsquelos.Add('');
  PageEsquelos.Values[0] := ExpandConstant('{userdesktop}\Nuevos esqueletos');
end;

function GetUserPath: string;
var
  CurrentPath: string;
begin
  if not RegQueryStringValue(HKCU, 'Environment', 'PATH', CurrentPath) then
    CurrentPath := '';
  Result := CurrentPath;
end;

procedure AddToUserPath(const PathToAdd: string);
var
  CurrentPath: string;
begin
  CurrentPath := GetUserPath;
  if Pos(LowerCase(PathToAdd), LowerCase(CurrentPath)) = 0 then
  begin
    if Length(CurrentPath) > 0 then
      CurrentPath := CurrentPath + ';';
    RegWriteStringValue(HKCU, 'Environment', 'PATH', CurrentPath + PathToAdd);
  end;
end;

procedure RemoveFromUserPath(const PathToRemove: string);
var
  CurrentPath, NewPath, Part: string;
  Idx: Integer;
begin
  CurrentPath := GetUserPath;
  NewPath := '';
  while CurrentPath <> '' do
  begin
    Idx := Pos(';', CurrentPath);
    if Idx = 0 then
    begin
      Part := CurrentPath;
      CurrentPath := '';
    end else
    begin
      Part := Copy(CurrentPath, 1, Idx - 1);
      CurrentPath := Copy(CurrentPath, Idx + 1, MaxInt);
    end;
    if (Part <> '') and (LowerCase(Part) <> LowerCase(PathToRemove)) then
    begin
      if NewPath <> '' then NewPath := NewPath + ';';
      NewPath := NewPath + Part;
    end;
  end;
  RegWriteStringValue(HKCU, 'Environment', 'PATH', NewPath);
end;

procedure WriteMavenSettings(AppDir: string);
var
  SettingsDir, SettingsFile, RepoPath, XML: string;
begin
  SettingsDir := AppDir + '\tools\maven-user';
  SettingsFile := SettingsDir + '\settings.xml';
  RepoPath := AppDir + '\tools\maven-user\repository';
  ForceDirectories(SettingsDir);
  XML := '<?xml version="1.0" encoding="UTF-8"?>' + #13#10 +
         '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"' + #13#10 +
         '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' + #13#10 +
         '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0' + #13#10 +
         '          https://maven.apache.org/xsd/settings-1.2.0.xsd">' + #13#10 +
         '  <localRepository>' + RepoPath + '</localRepository>' + #13#10 +
         '  <offline>true</offline>' + #13#10 +
         '</settings>';
  SaveStringToFile(SettingsFile, XML, False);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  AppDir: string;
  ResultCode: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    AppDir := ExpandConstant('{app}');
    AddToUserPath(AppDir + '\tools\jdk\bin');
    AddToUserPath(AppDir + '\tools\maven\bin');
    AddToUserPath(AppDir + '\tools\nodejs');
    { Configurar Maven para usar el repositorio local bundleado }
    RegWriteStringValue(HKCU, 'Environment', 'MAVEN_USER_HOME', AppDir + '\tools\maven-user');
    WriteMavenSettings(AppDir);
    { Importar configuracion de proyectos con las rutas elegidas por el usuario }
    if FileExists(AppDir + '\proyectos_template.json') then
    begin
      Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'),
        '-NonInteractive -ExecutionPolicy Bypass -File "' + AppDir + '\setup_config.ps1"' +
        ' -Proyectos "' + PageProyectos.Values[0] + '"' +
        ' -Informes "' + PageInformes.Values[0] + '"' +
        ' -Esqueletos "' + PageEsquelos.Values[0] + '"' +
        ' -TemplateFile "' + AppDir + '\proyectos_template.json"' +
        ' -ConfigDir "' + ExpandConstant('{localappdata}\OrquestadorAutomatizaciones') + '"',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    end;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  AppDir: string;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    AppDir := ExpandConstant('{app}');
    RemoveFromUserPath(AppDir + '\tools\jdk\bin');
    RemoveFromUserPath(AppDir + '\tools\maven\bin');
    RemoveFromUserPath(AppDir + '\tools\nodejs');
    RegDeleteValue(HKCU, 'Environment', 'MAVEN_USER_HOME');
  end;
end;
"@

$issFile = "$DIST\installer.iss"
[System.IO.File]::WriteAllText($issFile, $issScript, [System.Text.Encoding]::UTF8)

& $ISCC $issFile
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup falló con código $LASTEXITCODE"
}

$setupExe = "$DIST\OrquestadorAutomatizaciones-Setup.exe"
if (Test-Path $setupExe) {
    $setupSize = [math]::Round((Get-Item $setupExe).Length / 1MB, 1)
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  LISTO! Instalador generado correctamente" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  Archivo: $setupExe" -ForegroundColor Green
    Write-Host "  Tamanio: $setupSize MB" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Distribuye ese archivo .exe a los otros equipos." -ForegroundColor Yellow
    Write-Host "  El instalador NO requiere Java instalado en el equipo." -ForegroundColor Yellow
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""

    # Actualizar JAR de App Maestra en carpeta local del maestro
    $maestroJar = "$TARGET\orquestador-automatizaciones-1.0-SNAPSHOT-maestro.jar"
    $maestroDest = "$env:LOCALAPPDATA\OrquestadorMaestro\app-maestra.jar"
    if (Test-Path $maestroJar) {
        New-Item -ItemType Directory -Path "$env:LOCALAPPDATA\OrquestadorMaestro" -Force | Out-Null
        Copy-Item $maestroJar -Destination $maestroDest -Force
        Write-Host "  App Maestra actualizada en: $maestroDest" -ForegroundColor Green
    }

    # Abrir carpeta dist en el Explorador
    Start-Process explorer.exe $DIST
} else {
    throw "No se encontró el instalador en $setupExe"
}
