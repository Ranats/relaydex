param(
    [string]$WebhookUrl
)

if ($WebhookUrl -and $WebhookUrl.Trim()) {
    return $WebhookUrl.Trim()
}

if ($env:RELAYDEX_DISCORD_WEBHOOK_URL -and $env:RELAYDEX_DISCORD_WEBHOOK_URL.Trim()) {
    return $env:RELAYDEX_DISCORD_WEBHOOK_URL.Trim()
}

$localConfig = Join-Path $PSScriptRoot "local\\discord-webhook.txt"
if (Test-Path $localConfig) {
    $value = (Get-Content $localConfig -Raw).Trim()
    if ($value) {
        return $value
    }
}

throw "Discord webhook URL is not configured. Set RELAYDEX_DISCORD_WEBHOOK_URL or create tools\\local\\discord-webhook.txt."
