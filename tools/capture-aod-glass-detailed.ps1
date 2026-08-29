param(
    [ValidateSet("start", "mark", "stop", "status")]
    [string]$Action = "status",
    [string]$Label = ""
)

$ErrorActionPreference = "Stop"

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adbCommand) {
    throw "adb was not found in PATH. Install Android platform-tools or add adb.exe to PATH."
}

$downloadDir = Join-Path $env:USERPROFILE "Downloads"
$statePath = Join-Path $downloadDir "aod-glass-detailed.state.json"

function Read-State {
    if (-not (Test-Path -LiteralPath $statePath)) {
        return $null
    }
    Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
}

function Write-State([object]$State) {
    $State | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding UTF8
}

function Get-CaptureProcess([object]$State) {
    if ($null -eq $State -or $null -eq $State.pid) {
        return $null
    }
    Get-Process -Id ([int]$State.pid) -ErrorAction SilentlyContinue
}

function Invoke-AdbText([string[]]$Arguments) {
    (& $adbCommand.Source @Arguments 2>&1 | Out-String).TrimEnd()
}

function Write-Snapshot([object]$State, [string]$Path) {
    $path = $Path
    $content = @(
        "HostTime=$(Get-Date -Format o)"
        "ADB=$(Invoke-AdbText @('get-state'))"
        "Devices:"
        (Invoke-AdbText @('devices', '-l'))
        "Build properties:"
        (Invoke-AdbText @('shell', 'getprop', 'ro.build.version.incremental'))
        (Invoke-AdbText @('shell', 'getprop', 'ro.miui.ui.version.name'))
        (Invoke-AdbText @('shell', 'getprop', 'ro.build.version.release'))
        (Invoke-AdbText @('shell', 'getprop', 'ro.product.device'))
        (Invoke-AdbText @('shell', 'getprop', 'ro.product.name'))
        "Relevant processes:"
        (Invoke-AdbText @('shell', 'ps', '-A'))
        "AOD package:"
        (Invoke-AdbText @('shell', 'dumpsys', 'package', 'com.miui.aod'))
        "SystemUI package:"
        (Invoke-AdbText @('shell', 'dumpsys', 'package', 'com.android.systemui'))
    )
    $content | Set-Content -LiteralPath $path -Encoding UTF8
    return $path
}

function Add-LocalMarker([object]$State, [string]$MarkerLabel) {
    if ([string]::IsNullOrWhiteSpace($MarkerLabel)) {
        throw "mark requires a label, for example: mark BeforeSwitch"
    }
    $hostTime = Get-Date -Format o
    $deviceTime = Invoke-AdbText @('shell', 'date', '+%Y-%m-%dT%H:%M:%S.%3N%z')
    Add-Content -LiteralPath $State.markersPath -Value "HostTime=$hostTime`tDeviceTime=$deviceTime`tLabel=$MarkerLabel" -Encoding UTF8
    Write-Host "Marker written: $MarkerLabel"
    Write-Host "Host time: $hostTime"
}

function Write-Summary([object]$State) {
    if (-not (Test-Path -LiteralPath $State.logPath)) {
        return
    }

    $patterns = @(
        "FreeformUnbounded",
        "LSPosedFramework: \(com\.android\.systemui",
        "LSPosedFramework: \(com\.miui\.aod",
        "Hooked .*MiuiClockController",
        "Hooked .*EffectsTemplateView",
        "installed .*hook",
        "profile class not found",
        "no known signature matched",
        "isWallpaperSupportGlassFilter",
        "disableGlassFilter",
        "enableGlassFilter",
        "glassEffectDisable",
        "computeSupportedClockEffect",
        "clockEffect=[0-9]+",
        "resourceType=(image|video|dynamic|live|super[^ ]*)",
        "magicType=(100000|[0-9]+)",
        "wallpaperSupportDepth",
        "Restored Glass",
        "Preserved Glass",
        "Enabled Glass",
        "Skipped AOD Glass",
        "FATAL EXCEPTION",
        "AndroidRuntime",
        "NoSuchMethod",
        "ClassNotFound",
        "IllegalArgumentException"
    )

    $summaryPath = $State.summaryPath
    $contextPath = $State.contextPath
    $regex = ($patterns -join "|")
    Select-String -LiteralPath $State.logPath -Pattern $regex |
        ForEach-Object { $_.Line } |
        Set-Content -LiteralPath $summaryPath -Encoding UTF8

    Select-String -LiteralPath $State.logPath -Pattern $regex -Context 4,4 |
        Out-File -LiteralPath $contextPath -Encoding UTF8

    $reportPath = $State.reportPath
    @(
        "AOD Glass detailed capture report"
        "Started: $($State.startedAt)"
        "Stopped: $(Get-Date -Format o)"
        "FullLog: $($State.logPath)"
        "Summary: $summaryPath"
        "Context: $contextPath"
        "Markers: $($State.markersPath)"
        "StartSnapshot: $($State.startSnapshotPath)"
        "EndSnapshot: $($State.endSnapshotPath)"
        ""
        "=== Markers ==="
        (Get-Content -LiteralPath $State.markersPath -ErrorAction SilentlyContinue)
        ""
        "=== High-signal events ==="
        (Get-Content -LiteralPath $summaryPath -ErrorAction SilentlyContinue)
    ) | Set-Content -LiteralPath $reportPath -Encoding UTF8
}

switch ($Action) {
    "start" {
        $oldState = Read-State
        if ($null -ne (Get-CaptureProcess $oldState)) {
            throw "A detailed capture is already running. Run stop first."
        }

        & $adbCommand.Source wait-for-device
        & $adbCommand.Source logcat -c

        $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
        $prefix = Join-Path $downloadDir "aod_glass_detailed_$stamp"
        $logPath = "$prefix.log"
        $errPath = "$prefix.adb.err"
        $summaryPath = "$prefix.summary.txt"
        $contextPath = "$prefix.context.txt"
        $markersPath = "$prefix.markers.txt"
        $reportPath = "$prefix.report.txt"
        New-Item -ItemType File -Path $markersPath -Force | Out-Null

        $state = [ordered]@{
            pid = $null
            startedAt = (Get-Date).ToString("o")
            directory = $downloadDir
            logPath = $logPath
            errPath = $errPath
            summaryPath = $summaryPath
            contextPath = $contextPath
            markersPath = $markersPath
            reportPath = $reportPath
            startSnapshotPath = "$prefix.start.txt"
            endSnapshotPath = "$prefix.end.txt"
        }
        Write-State $state
        $state.startSnapshotPath = Write-Snapshot $state $state.startSnapshotPath

        $captureProcess = Start-Process `
            -FilePath $adbCommand.Source `
            -ArgumentList @("logcat", "-v", "threadtime", "-b", "all") `
            -RedirectStandardOutput $logPath `
            -RedirectStandardError $errPath `
            -PassThru `
            -WindowStyle Hidden

        $state.pid = $captureProcess.Id
        Write-State $state
        Add-LocalMarker $state "CAPTURE_START"

        Write-Host "Detailed capture started. PID: $($captureProcess.Id)"
        Write-Host "Full log: $logPath"
        Write-Host "Start snapshot: $($state.startSnapshotPath)"
        Write-Host ""
        Write-Host "Run markers at important points:"
        Write-Host "  powershell -ExecutionPolicy Bypass -File .\tools\capture-aod-glass-detailed.ps1 mark BeforeSwitch"
        Write-Host "  powershell -ExecutionPolicy Bypass -File .\tools\capture-aod-glass-detailed.ps1 mark AfterSwitch"
        Write-Host "  powershell -ExecutionPolicy Bypass -File .\tools\capture-aod-glass-detailed.ps1 mark BeforeApply"
        Write-Host "  powershell -ExecutionPolicy Bypass -File .\tools\capture-aod-glass-detailed.ps1 mark AfterApply"
        Write-Host "When the result is visible, run: powershell -ExecutionPolicy Bypass -File .\tools\capture-aod-glass-detailed.ps1 stop"
    }

    "mark" {
        $state = Read-State
        if ($null -eq $state -or $null -eq (Get-CaptureProcess $state)) {
            throw "No active detailed capture. Run start first."
        }
        Add-LocalMarker $state $Label
    }

    "stop" {
        $state = Read-State
        if ($null -eq $state) {
            throw "No detailed capture state found. Run start first."
        }

        $captureProcess = Get-CaptureProcess $state
        if ($null -ne $captureProcess) {
            Add-LocalMarker $state "CAPTURE_STOP_REQUESTED"
            Stop-Process -Id $captureProcess.Id -Force
            Start-Sleep -Milliseconds 700
        }

        $state.endSnapshotPath = Write-Snapshot $state $state.endSnapshotPath
        Write-Summary $state
        Remove-Item -LiteralPath $statePath -Force

        Write-Host "Detailed capture stopped."
        Write-Host "Full log: $($state.logPath)"
        Write-Host "Summary: $($state.summaryPath)"
        Write-Host "Context: $($state.contextPath)"
        Write-Host "Report: $($state.reportPath)"
        Write-Host "Markers: $($state.markersPath)"
        Write-Host "Start snapshot: $($state.startSnapshotPath)"
        Write-Host "End snapshot: $($state.endSnapshotPath)"
        Write-Host "ADB errors: $($state.errPath)"
    }

    "status" {
        $state = Read-State
        $captureProcess = Get-CaptureProcess $state
        if ($null -eq $state -or $null -eq $captureProcess) {
            Write-Host "No active detailed capture."
        } else {
            Write-Host "Running (PID $($captureProcess.Id)); started $($state.startedAt)"
            Write-Host "Full log: $($state.logPath)"
            Write-Host "Markers: $($state.markersPath)"
        }
    }
}
