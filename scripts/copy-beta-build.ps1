param(
    [string]$Source = "",
    [string]$Name = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$betaDestination = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot "..\..\Beta.Test.Build")
)

if ([string]::IsNullOrWhiteSpace($Source)) {
    $Source = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
}
$resolvedSource = (Resolve-Path -LiteralPath $Source).Path
if ([string]::IsNullOrWhiteSpace($Name)) {
    $buildConfig = Get-Content -LiteralPath (Join-Path $repoRoot "app\build.gradle.kts") -Raw
    $versionMatch = [regex]::Match($buildConfig, 'versionName\s*=\s*"([^"]+)"')
    if (-not $versionMatch.Success) {
        throw "Unable to resolve versionName from app/build.gradle.kts"
    }
    $Name = "weaverse-v$($versionMatch.Groups[1].Value)-local.apk"
}

New-Item -ItemType Directory -Path $betaDestination -Force | Out-Null
$destinationFile = Join-Path $betaDestination $Name
Copy-Item -LiteralPath $resolvedSource -Destination $destinationFile -Force
Get-Item -LiteralPath $destinationFile |
    Select-Object FullName, Length, LastWriteTime
