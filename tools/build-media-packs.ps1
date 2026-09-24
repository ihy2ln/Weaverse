<#
.SYNOPSIS
    Re-encodes the Adams Haven Godot art into the Weaverse core asset set and media packs.

.DESCRIPTION
    Thin wrapper over tools/build_media_packs.py so the pipeline can be run the same way as
    tools/stage-storyboard-assets.ps1. The Godot art tree named in tools/media-packs.json is
    only ever read; output goes to app/src/main/assets (core, shipped in the APK) and
    build/media-packs (optional downloadable packs).

.EXAMPLE
    ./tools/build-media-packs.ps1 -DryRun
    ./tools/build-media-packs.ps1 -Only monsters,arenas
    ./tools/build-media-packs.ps1 -Force
#>
[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$Force,
    [switch]$NoZip,
    [string[]]$Only
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$script = Join-Path $PSScriptRoot 'build_media_packs.py'
if (-not (Test-Path $script)) { throw "Missing $script" }

$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $python) { $python = (Get-Command python3 -ErrorAction SilentlyContinue).Source }
$portablePython = 'S:\AI\ComfyUI_windows_portable\python_embeded\python.exe'
if (-not $python -and (Test-Path -LiteralPath $portablePython)) { $python = $portablePython }
if (-not $python) { throw 'Python 3 with Pillow is required and was not found on PATH.' }

$arguments = @($script)
if ($DryRun) { $arguments += '--dry-run' }
if ($Force) { $arguments += '--force' }
if ($NoZip) { $arguments += '--no-zip' }
if ($Only) { $arguments += @('--only', ($Only -join ',')) }

Push-Location $repoRoot
try {
    & $python @arguments
    if ($LASTEXITCODE -ne 0) { throw "build_media_packs.py exited with $LASTEXITCODE" }
}
finally {
    Pop-Location
}
