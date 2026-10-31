param(
    [string]$ApkPath = "app/build/outputs/apk/release/app-release.apk",
    [string]$Label = "experiment"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$resolvedApk = Join-Path $root $ApkPath
if (-not (Test-Path -LiteralPath $resolvedApk -PathType Leaf)) {
    throw "APK not found: $resolvedApk. Build the standard Gradle APK first."
}

$versionJson = & python (Join-Path $PSScriptRoot "verify-version.py") --json
if ($LASTEXITCODE -ne 0) { throw "Version metadata is invalid." }
$version = $versionJson | ConvertFrom-Json
$versionName = $version.versionName
$versionCode = $version.versionCode

& python (Join-Path $PSScriptRoot "verify-release-apk.py") --apk $resolvedApk
if ($LASTEXITCODE -ne 0) { throw "APK does not match the current release metadata." }

$safeLabel = ($Label -replace '[^0-9A-Za-z._-]+', '-').Trim('-')
if ([string]::IsNullOrWhiteSpace($safeLabel)) {
    $safeLabel = "experiment"
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmssfff"
$experimentRoot = Join-Path $root "experiments"
$experimentDir = Join-Path $experimentRoot "$stamp-$safeLabel"
New-Item -ItemType Directory -Path $experimentDir -Force | Out-Null

$apkName = "FreeformUnbounded-v$versionName-$stamp.apk"
$experimentApk = Join-Path $experimentDir $apkName
Copy-Item -LiteralPath $resolvedApk -Destination $experimentApk
$hash = (Get-FileHash -LiteralPath $experimentApk -Algorithm SHA256).Hash

$metadata = @(
    "ExperimentTimestamp=$stamp"
    "Label=$safeLabel"
    "VersionName=$versionName"
    "VersionCode=$versionCode"
    "StandardApk=$resolvedApk"
    "ExperimentApk=$experimentApk"
    "SHA256=$hash"
    "ExportedAt=$(Get-Date -Format o)"
)
$metadata | Set-Content -LiteralPath (Join-Path $experimentDir "manifest.txt") -Encoding UTF8

Write-Host "Standard APK preserved: $resolvedApk"
Write-Host "Experiment folder: $experimentDir"
Write-Host "Experiment APK: $experimentApk"
Write-Host "SHA256: $hash"
