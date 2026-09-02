[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string[]]$ImagePath,

    [string]$InboxDirectory = "",
    [switch]$PushToDevice,
    [string]$AdbPath = "adb",
    [string]$DeviceSerial = "",
    [string]$DeviceDirectory = "/sdcard/Download/Weaverse-Storyboard-Inbox"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($InboxDirectory)) {
    $InboxDirectory = Join-Path $PSScriptRoot "storyboard-inbox"
}
$resolvedInbox = [System.IO.Path]::GetFullPath($InboxDirectory)
New-Item -ItemType Directory -Path $resolvedInbox -Force | Out-Null

$supportedExtensions = @(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif")

function Get-VersionedDestination {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$FileName
    )
    $candidate = Join-Path $Directory $FileName
    if (-not (Test-Path -LiteralPath $candidate)) { return $candidate }

    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($FileName)
    $extension = [System.IO.Path]::GetExtension($FileName)
    $version = 2
    do {
        $candidate = Join-Path $Directory ("{0}-{1}{2}" -f $baseName, $version, $extension)
        $version++
    } while (Test-Path -LiteralPath $candidate)
    return $candidate
}

$staged = foreach ($path in $ImagePath) {
    $source = Get-Item -LiteralPath $path -ErrorAction Stop
    if (-not $source.PSIsContainer -and $source.Extension.ToLowerInvariant() -in $supportedExtensions) {
        $destination = Get-VersionedDestination -Directory $resolvedInbox -FileName $source.Name
        Copy-Item -LiteralPath $source.FullName -Destination $destination
        Get-Item -LiteralPath $destination
    } else {
        throw "Unsupported storyboard asset: $path. Use PNG, JPG/JPEG, WEBP, BMP, or GIF."
    }
}

Write-Host "Staged storyboard assets (source files were not changed):"
$staged | ForEach-Object { Write-Host ("  " + $_.FullName) }

if (-not $PushToDevice) {
    Write-Host ""
    Write-Host "Next: copy/open these files on Android, then in Weaverse choose Storyboard -> select an empty slot -> Import generated panel."
    Write-Host "To push with adb, rerun with -PushToDevice and optionally -AdbPath/-DeviceSerial."
    return
}

$adb = Get-Command $AdbPath -ErrorAction SilentlyContinue
if ($null -eq $adb) {
    Write-Warning "adb is unavailable. Assets remain safely staged at: $resolvedInbox"
    Write-Host "Install Android platform-tools or pass -AdbPath <full-path-to-adb>, then rerun with -PushToDevice."
    Write-Host "Manual alternative: copy the staged files to the device Downloads folder and use Weaverse's Android picker."
    return
}

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $connected = & $adb.Source devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
    if ($connected.Count -ne 1) {
        Write-Warning "Expected exactly one connected adb device, found $($connected.Count). Assets remain at: $resolvedInbox"
        Write-Host "Rerun with -DeviceSerial <serial> after checking 'adb devices'."
        return
    }
    $DeviceSerial = (($connected | Select-Object -First 1) -split "\s+")[0]
}

$adbPrefix = @("-s", $DeviceSerial)
$runFolder = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss") + "-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$remoteDirectory = $DeviceDirectory.TrimEnd("/") + "/" + $runFolder
& $adb.Source @adbPrefix shell mkdir -p $remoteDirectory
if ($LASTEXITCODE -ne 0) { throw "adb could not create $remoteDirectory" }

Write-Host ""
Write-Host "Pushed storyboard assets:"
foreach ($asset in $staged) {
    $remotePath = $remoteDirectory + "/" + $asset.Name
    & $adb.Source @adbPrefix push $asset.FullName $remotePath | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "adb failed while pushing $($asset.FullName)" }
    Write-Host ("  " + $remotePath)
}
Write-Host ""
Write-Host "On Android: Weaverse -> Storyboard -> select an empty layout slot -> Import generated panel -> Downloads/Weaverse-Storyboard-Inbox."
