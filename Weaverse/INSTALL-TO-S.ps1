# Installs the Weaverse Windows / web / sync package to S:\AI\Novel\Weaverse
# Run on the Windows PC that has the S: drive.
$ErrorActionPreference = "Stop"
$Repo = "ihy2ln/weaverse"
$Dest = "S:\AI\Novel\Weaverse"
$Source = Split-Path -Parent $MyInvocation.MyCommand.Path

function Ensure-Dir([string]$Path) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

if (-not (Test-Path "S:\")) {
    Write-Host "S: drive was not found. Using $env:USERPROFILE\Weaverse instead."
    Write-Host "Map or create S: and re-run this script to use S:\AI\Novel\Weaverse."
    $Dest = Join-Path $env:USERPROFILE "Weaverse"
}

Write-Host "Installing Weaverse to $Dest"
Ensure-Dir $Dest
Ensure-Dir (Join-Path $Dest "data")

$copyNames = @(
    "Weaverse.exe",
    "Weaverse.jar",
    "START-DESKTOP.bat",
    "START-WEB.bat",
    "START-DESKTOP.sh",
    "README.md",
    "SYNC.md",
    "START-HERE.txt",
    "COPY-HERE.txt",
    "INSTALL-TO-S.ps1"
)
foreach ($name in $copyNames) {
    $from = Join-Path $Source $name
    if (Test-Path $from) {
        Copy-Item -Path $from -Destination (Join-Path $Dest $name) -Force
    }
}

$needExe = -not (Test-Path (Join-Path $Dest "Weaverse.exe"))
$needJar = -not (Test-Path (Join-Path $Dest "Weaverse.jar"))
if ($needExe -or $needJar) {
    Write-Host "Downloading missing desktop files from GitHub Releases ($Repo)…"
    $api = "https://api.github.com/repos/$Repo/releases/latest"
    $release = Invoke-RestMethod -Uri $api -Headers @{ "User-Agent" = "Weaverse-Installer" }
    foreach ($asset in $release.assets) {
        $name = $asset.name
        $url = $asset.browser_download_url
        if ($needExe -and ($name -eq "Weaverse.exe" -or $name -like "Weaverse-*.exe")) {
            $out = Join-Path $Dest "Weaverse.exe"
            Write-Host "  $name -> Weaverse.exe"
            Invoke-WebRequest -Uri $url -OutFile $out
            $needExe = $false
        }
        if ($needJar -and $name -eq "Weaverse.jar") {
            Write-Host "  $name"
            Invoke-WebRequest -Uri $url -OutFile (Join-Path $Dest "Weaverse.jar")
            $needJar = $false
        }
        if ($name -eq "START-DESKTOP.bat" -and -not (Test-Path (Join-Path $Dest "START-DESKTOP.bat"))) {
            Invoke-WebRequest -Uri $url -OutFile (Join-Path $Dest "START-DESKTOP.bat")
        }
        if ($name -eq "START-WEB.bat" -and -not (Test-Path (Join-Path $Dest "START-WEB.bat"))) {
            Invoke-WebRequest -Uri $url -OutFile (Join-Path $Dest "START-WEB.bat")
        }
    }
}

$here = @"
Weaverse is installed here: $Dest

Double-click Weaverse.exe or START-DESKTOP.bat
Web UI: http://127.0.0.1:8787
Sync: Android Settings → Sync → peer http://<pc-ip>:8787 + Pair PIN

GitHub: https://github.com/$Repo/releases
"@
Set-Content -Path (Join-Path $Dest "START-HERE.txt") -Value $here -Encoding UTF8

Write-Host ""
Write-Host "Done. Folder: $Dest"
Write-Host "Run START-DESKTOP.bat or Weaverse.exe there."
Write-Host "Web companion: http://127.0.0.1:8787"
