param(
    [ValidateSet("start", "stop", "status")]
    [string]$Action = "status"
)

$ErrorActionPreference = "Stop"

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adbCommand) {
    throw "adb not found in PATH. Install Android platform-tools or add adb.exe to PATH."
}

$downloadDir = Join-Path $env:USERPROFILE "Downloads"
$statePath = Join-Path $downloadDir "aod-glass-capture.state.json"

function Read-State {
    if (-not (Test-Path -LiteralPath $statePath)) {
        return $null
    }
    return Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
}

function Write-State([object]$state) {
    $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
}

function Get-CaptureProcess([object]$state) {
    if ($null -eq $state -or $null -eq $state.pid) {
        return $null
    }
    return Get-Process -Id ([int]$state.pid) -ErrorAction SilentlyContinue
}

switch ($Action) {
    "start" {
        $oldState = Read-State
        $oldProcess = Get-CaptureProcess $oldState
        if ($null -ne $oldProcess) {
            throw "A capture is already running (PID $($oldProcess.Id)). Run stop first."
        }

        & $adbCommand.Source wait-for-device
        & $adbCommand.Source logcat -c

        $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
        $logPath = Join-Path $downloadDir "aod_glass_$stamp.log"
        $errPath = Join-Path $downloadDir "aod_glass_$stamp.err"
        $filteredPath = "$logPath.filtered.txt"

        $captureProcess = Start-Process `
            -FilePath $adbCommand.Source `
            -ArgumentList @("logcat", "-v", "threadtime", "-b", "all") `
            -RedirectStandardOutput $logPath `
            -RedirectStandardError $errPath `
            -PassThru `
            -WindowStyle Hidden

        Write-State ([ordered]@{
            pid = $captureProcess.Id
            startedAt = (Get-Date).ToString("o")
            logPath = $logPath
            errPath = $errPath
            filteredPath = $filteredPath
        })

        Write-Host "Capture started. PID: $($captureProcess.Id)"
        Write-Host "Log: $logPath"
        Write-Host "Now reproduce: static Glass -> switch to dynamic wallpaper -> open number styles -> apply."
        Write-Host "When finished, run: .\capture-aod-glass-log.ps1 stop"
    }

    "stop" {
        $state = Read-State
        if ($null -eq $state) {
            throw "No capture state found. Run start first."
        }

        $captureProcess = Get-CaptureProcess $state
        if ($null -ne $captureProcess) {
            Stop-Process -Id $captureProcess.Id -Force
            Start-Sleep -Milliseconds 300
        }

        if (Test-Path -LiteralPath $state.logPath) {
            Select-String `
                -Path $state.logPath `
                -Pattern "FreeformUnbounded|com.miui.aod|Keyguard-Editor|EffectsTemplateView|EditFragmentViewModel|glass|Glass|clockEffect|disableGlassFilter|profile class|installed .*hook" `
                -Context 3,3 `
                | Out-File -LiteralPath $state.filteredPath -Encoding UTF8
        }

        Remove-Item -LiteralPath $statePath -Force
        Write-Host "Capture stopped."
        Write-Host "Full log: $($state.logPath)"
        Write-Host "Filtered log: $($state.filteredPath)"
        Write-Host "ADB errors: $($state.errPath)"
    }

    "status" {
        $state = Read-State
        $captureProcess = Get-CaptureProcess $state
        if ($null -ne $captureProcess) {
            Write-Host "Running (PID $($captureProcess.Id)); started $($state.startedAt)"
            Write-Host "Log: $($state.logPath)"
        } else {
            Write-Host "No active capture."
        }
    }
}
