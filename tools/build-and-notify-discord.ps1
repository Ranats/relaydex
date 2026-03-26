param(
    [string]$SourceRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$BuildRoot = "C:\\Users\\sopur\\CodexBuild\\remodex",
    [string]$OutputDir = "C:\\Users\\sopur\\Documents\\RelaydexBuilds",
    [string]$SessionWorkdir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$WebhookUrl,
    [switch]$SkipSessionStart
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $BuildRoot | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$robocopyArgs = @(
    $SourceRepoRoot,
    $BuildRoot,
    "/MIR",
    "/XD", ".git", "node_modules", "marketing", ".gradle", "build", "tools\\local", "android\\.gradle", "android\\app\\build", "phodex-bridge\\node_modules",
    "/XF", "android\\local.properties"
)
robocopy @robocopyArgs | Out-Null

$androidSdk = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} elseif (Test-Path "C:\\Users\\sopur\\AppData\\Local\\Android\\Sdk") {
    "C:\\Users\\sopur\\AppData\\Local\\Android\\Sdk"
} else {
    $null
}

$localPropertiesPath = Join-Path $BuildRoot "android\\local.properties"
if ($androidSdk) {
    $sdkPath = $androidSdk.Replace("\\", "\\\\")
    Set-Content -Path $localPropertiesPath -Value "sdk.dir=$sdkPath" -Encoding ASCII
}

$androidRoot = Join-Path $BuildRoot "android"
Push-Location $androidRoot
try {
    .\gradlew.bat assembleDebug assembleRelease | Out-Host
} finally {
    Pop-Location
}

$debugApk = Join-Path $androidRoot "app\\build\\outputs\\apk\\debug\\app-debug.apk"
$releaseApk = Join-Path $androidRoot "app\\build\\outputs\\apk\\release\\app-release.apk"
$copiedDebugApk = Join-Path $OutputDir "relaydex-debug-latest.apk"
$copiedReleaseApk = Join-Path $OutputDir "relaydex-release-latest.apk"
Copy-Item $debugApk $copiedDebugApk -Force
Copy-Item $releaseApk $copiedReleaseApk -Force

$buildSummaryPath = Join-Path $OutputDir "relaydex-build-latest.txt"
$buildSummary = @(
    "Relaydex Android build complete"
    "Built at: $(Get-Date -Format s)"
    "Source repo: $SourceRepoRoot"
    "Build root: $BuildRoot"
    "Session workdir: $SessionWorkdir"
    "Debug APK: $copiedDebugApk"
    "Release APK: $copiedReleaseApk"
)
$buildSummary | Set-Content -Path $buildSummaryPath -Encoding UTF8

& (Join-Path $PSScriptRoot "Send-DiscordWebhook.ps1") `
    -WebhookUrl $WebhookUrl `
    -Content "Relaydex Android build is ready. APKs are attached." `
    -Files @($buildSummaryPath, $copiedReleaseApk, $copiedDebugApk)

if (-not $SkipSessionStart) {
    & (Join-Path $PSScriptRoot "start-relaydex-and-notify.ps1") `
        -WebhookUrl $WebhookUrl `
        -OutputDir $OutputDir `
        -Workdir $SessionWorkdir
}
