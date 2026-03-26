param(
    [string]$Workdir = (Get-Location).Path,
    [string]$OutputDir = "C:\\Users\\sopur\\Documents\\RelaydexBuilds",
    [string]$WebhookUrl,
    [switch]$SkipResetPairing,
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stdoutPath = Join-Path $OutputDir "relaydex-up-$timestamp.stdout.log"
$stderrPath = Join-Path $OutputDir "relaydex-up-$timestamp.stderr.log"
$pairingJsonPath = Join-Path $OutputDir "relaydex-pairing-$timestamp.json"
$summaryPath = Join-Path $OutputDir "relaydex-session-$timestamp.txt"

if (-not $SkipResetPairing) {
    relaydex reset-pairing | Out-Null
}

$command = "relaydex up 1> `"$stdoutPath`" 2> `"$stderrPath`""
$process = Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/d", "/c", $command `
    -WorkingDirectory $Workdir `
    -WindowStyle Hidden `
    -PassThru

$outputBuffer = New-Object System.Collections.Generic.List[string]

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$pairingJson = $null
$sessionId = $null
$deviceId = $null
$relayUrl = $null
$expiresAt = $null

while ((Get-Date) -lt $deadline) {
    if ($process.HasExited) {
        break
    }

    if (Test-Path $stdoutPath) {
        $outputBuffer.Clear()
        foreach ($line in Get-Content $stdoutPath) {
            $outputBuffer.Add($line)
        }
    }

    foreach ($line in @($outputBuffer.ToArray())) {
        if (-not $sessionId -and $line -match "^Session ID:\\s+(.*)$") {
            $sessionId = $Matches[1].Trim()
        }
        if (-not $relayUrl -and $line -match "^Relay:\\s+(.*)$") {
            $relayUrl = $Matches[1].Trim()
        }
        if (-not $deviceId -and $line -match "^Device ID:\\s+(.*)$") {
            $deviceId = $Matches[1].Trim()
        }
        if (-not $expiresAt -and $line -match "^Expires:\\s+(.*)$") {
            $expiresAt = $Matches[1].Trim()
        }
    }

    $pairingMarkerIndex = $outputBuffer.FindIndex([Predicate[string]]{
        param($value)
        $value -like "Pairing payload*"
    })
    if ($pairingMarkerIndex -ge 0 -and $pairingMarkerIndex + 1 -lt $outputBuffer.Count) {
        $candidate = $outputBuffer[$pairingMarkerIndex + 1].Trim()
        if ($candidate.StartsWith("{") -and $candidate.EndsWith("}")) {
            $pairingJson = $candidate
            break
        }
    }

    Start-Sleep -Milliseconds 250
}

if (-not $pairingJson) {
    throw "Failed to capture pairing payload from relaydex up within $TimeoutSeconds seconds."
}

$pairingData = $pairingJson | ConvertFrom-Json
if (-not $sessionId) {
    $sessionId = $pairingData.sessionId
}
if (-not $relayUrl) {
    $relayUrl = $pairingData.relay
}
if (-not $deviceId) {
    $deviceId = $pairingData.macDeviceId
}
if (-not $expiresAt -and $pairingData.expiresAt) {
    $expiresAt = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$pairingData.expiresAt).ToString("u")
}

Set-Content -Path $pairingJsonPath -Value $pairingJson -Encoding UTF8

$summary = @(
    "Relaydex session started"
    "Started at: $(Get-Date -Format s)"
    "PID: $($process.Id)"
    "Working directory: $Workdir"
    "Session ID: $sessionId"
    "Device ID: $deviceId"
    "Relay: $relayUrl"
    "Expires: $expiresAt"
    ""
    "Paste the attached JSON into the Android app if you are not scanning the QR in the terminal."
)
$summary | Set-Content -Path $summaryPath -Encoding UTF8

$latestApks = @(
    (Join-Path $OutputDir "relaydex-release-latest.apk"),
    (Join-Path $OutputDir "relaydex-debug-latest.apk")
) | Where-Object { Test-Path $_ }

$content = @"
Relaydex session is ready.
Working directory: $Workdir
Session ID: $sessionId
Device ID: $deviceId
Expires: $expiresAt

The attached pairing JSON can be pasted into the Android app.
The relay process is still running on the host.
"@

$filesToUpload = @($summaryPath, $pairingJsonPath, $stdoutPath)
$filesToUpload += $latestApks

& (Join-Path $PSScriptRoot "Send-DiscordWebhook.ps1") `
    -WebhookUrl $WebhookUrl `
    -Content $content `
    -Files $filesToUpload

[pscustomobject]@{
    ProcessId = $process.Id
    SessionId = $sessionId
    DeviceId = $deviceId
    ExpiresAt = $expiresAt
    PairingJsonPath = $pairingJsonPath
    StdoutPath = $stdoutPath
    StderrPath = $stderrPath
}
