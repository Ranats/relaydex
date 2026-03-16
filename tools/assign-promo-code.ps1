param(
  [Parameter(Mandatory = $true)]
  [string]$CodeCsvPath,

  [Parameter(Mandatory = $true)]
  [string]$RequesterId,

  [string]$AssignmentsCsvPath = ".\\promotion_code_assignments.csv",
  [string]$Notes = ""
)

$codes = Import-Csv -Path $CodeCsvPath
if (-not $codes) {
  throw "No promo codes were found in $CodeCsvPath"
}

if (-not (Test-Path $AssignmentsCsvPath)) {
  "AssignedAt,RequesterId,PromotionCode,Notes" | Set-Content -Path $AssignmentsCsvPath
}

$assignments = Import-Csv -Path $AssignmentsCsvPath
$existing = $assignments | Where-Object { $_.RequesterId -eq $RequesterId } | Select-Object -First 1
if ($existing) {
  Write-Output "Requester already has a code: $($existing.PromotionCode)"
  exit 0
}

$usedCodes = @{}
foreach ($assignment in $assignments) {
  if ($assignment.PromotionCode) {
    $usedCodes[$assignment.PromotionCode] = $true
  }
}

$next = $codes | Where-Object { -not $usedCodes.ContainsKey($_.'Promotion code') } | Select-Object -First 1
if (-not $next) {
  throw "No unused promo codes remain in $CodeCsvPath"
}

$row = [pscustomobject]@{
  AssignedAt = (Get-Date).ToString("s")
  RequesterId = $RequesterId
  PromotionCode = $next.'Promotion code'
  Notes = $Notes
}

$row | Export-Csv -Path $AssignmentsCsvPath -Append -NoTypeInformation
Write-Output $row.PromotionCode
