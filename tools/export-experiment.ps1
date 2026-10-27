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

$gradlePath = Join-Path $root "app/build.gradle.kts"
$buildConfigPath = Join-Path $root "app/src/main/java/com/freeform/unbounded/BuildConfig.kt"
$modulePropPath = Join-Path $root "app/src/main/resources/META-INF/xposed/module.prop"

function Read-Version([string]$Path, [string]$Pattern, [string]$Name) {
    $content = Get-Content -LiteralPath $Path -Raw
    $match = [regex]::Match($content, $Pattern)
    if (-not $match.Success) {
        throw "Unable to read $Name from $Path"
    }
    return $match.Groups[1].Value
}

$versionName = Read-Version $gradlePath 'versionName\s*=\s*"([^\"]+)"' "versionName"
$versionCode = Read-Version $gradlePath 'versionCode\s*=\s*(\d+)' "versionCode"
$buildConfigName = Read-Version $buildConfigPath 'VERSION_NAME:\s*String\s*=\s*"([^\"]+)"' "BuildConfig VERSION_NAME"
$buildConfigCode = Read-Version $buildConfigPath 'VERSION_CODE:\s*Int\s*=\s*(\d+)' "BuildConfig VERSION_CODE"
$moduleName = Read-Version $modulePropPath '(?m)^version=v([^\r\n]+)\r?$' "module.prop version"
$moduleCode = Read-Version $modulePropPath '(?m)^versionCode=(\d+)\r?$' "module.prop versionCode"

if ($versionName -ne $buildConfigName -or $versionName -ne $moduleName -or
    $versionCode -ne $buildConfigCode -or $versionCode -ne $moduleCode) {
    throw "Version metadata is inconsistent. Run .\tools\verify-version.py."
}

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
