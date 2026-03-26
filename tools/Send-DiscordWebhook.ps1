param(
    [Parameter(Mandatory = $true)]
    [string]$Content,

    [string[]]$Files = @(),

    [string]$WebhookUrl
)

$resolvedWebhook = & (Join-Path $PSScriptRoot "Get-DiscordWebhook.ps1") -WebhookUrl $WebhookUrl

$payloadJson = @{
    username = "Relaydex Bot"
    content = $Content
} | ConvertTo-Json -Compress

$arguments = @(
    "-sS",
    "-X", "POST",
    $resolvedWebhook,
    "--form-string", "payload_json=$payloadJson"
)

$index = 0
foreach ($file in $Files) {
    if (-not $file -or -not (Test-Path $file)) {
        continue
    }
    $arguments += "-F"
    $arguments += "file$index=@$file"
    $index += 1
}

& curl.exe @arguments | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Discord webhook upload failed with exit code $LASTEXITCODE."
}
