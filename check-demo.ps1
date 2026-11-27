param(
    [string]$CatalogUrl = 'http://127.0.0.1:8081',
    [string]$OptimizerUrl = 'http://127.0.0.1:8082',
    [string]$GatewayUrl = 'http://127.0.0.1:8080',
    [string]$UserUrl = 'http://127.0.0.1:8083',
    [string]$ChatbotUrl = 'http://127.0.0.1:8000',
    [string]$FrontendUrl = 'http://127.0.0.1:5173'
)
$ErrorActionPreference = 'Stop'
$checks = [System.Collections.Generic.List[object]]::new()
$fixturePath = Join-Path $PSScriptRoot 'BackendNutriMenu\foodcatalogservice\src\main\resources\catalog\nin-bulk-20261126\nin-source-import.json'
$fixture = Get-Content -LiteralPath $fixturePath -Raw -Encoding UTF8 | ConvertFrom-Json

function Add-Check([string]$Name, [bool]$Passed, [string]$Detail) {
    $checks.Add([pscustomobject]@{ Check = $Name; Status = $(if ($Passed) { 'PASS' } else { 'FAIL' }); Detail = $Detail })
}

function Read-Json([string]$Name, [string]$Url, $Body = $null) {
    try {
        if ($null -eq $Body) {
            $payload = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 25
        } else {
            $payload = Invoke-RestMethod -Uri $Url -Method Post -ContentType 'application/json; charset=utf-8' -Body ($Body | ConvertTo-Json -Depth 10) -TimeoutSec 25
        }
        if ($null -eq $payload) { throw 'Server returned a null JSON response.' }
        return ,$payload
    } catch {
        Add-Check $Name $false $_.Exception.Message
        return $null
    }
}

$health = Read-Json 'Gateway' "$GatewayUrl/api/health"
if ($health) { Add-Check 'Gateway' ($health.status -eq 'UP' -and $health.service -eq 'nutrimenu-java-gateway') 'Java gateway liveness; downstream services are checked separately.' }

$jwks = Read-Json 'JWT signing keys' "$GatewayUrl/api/auth/jwks"
if ($jwks) { Add-Check 'JWT signing keys' (@($jwks.keys | Where-Object { $_.kty -eq 'RSA' -and $_.kid -and $_.n -and $_.e -and -not $_.d }).Count -gt 0) 'Public RSA JWK only; private signing key must not be exposed.' }
try {
    Invoke-WebRequest -Uri "$GatewayUrl/api/foods" -UseBasicParsing -TimeoutSec 15 | Out-Null
    Add-Check 'JWT required for catalog' $false 'Unauthenticated request was accepted.'
} catch {
    $code = 0
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Add-Check 'JWT required for catalog' ($code -eq 401) "Unauthenticated catalog HTTP status: $code (expected 401)."
}

$userService = Read-Json 'User Service' "$UserUrl/api/auth/csrf"
if ($userService) { Add-Check 'User Service' ([bool]$userService.headerName -and [bool]$userService.token) 'Public session endpoint responds; no login or profile was changed.' }
$chatService = Read-Json 'Chatbot' "$ChatbotUrl/health"
if ($chatService) { Add-Check 'Chatbot' ($chatService.status -eq 'ok') 'Liveness only; use the browser to test an actual answer.' }

$ready = Read-Json 'Catalog readiness' "$CatalogUrl/api/v1/internal/dishes/readiness"
if ($ready) {
    $current = $ready.datasetId -eq $fixture.datasetId -and $ready.contractVersion -ge 3 -and $ready.optionalNutritionSupported -eq $true -and $ready.perAllergenEvidence -eq $true
    Add-Check 'Current catalog code' $current "Running dataset: $($ready.datasetId). Expected: $($fixture.datasetId). Restart Catalog if different."
    Add-Check 'Imported catalog rows' ($ready.loadedSourceDishCount -eq @($fixture.dishes).Count) "Loaded $($ready.loadedSourceDishCount) / $(@($fixture.dishes).Count); general eligible $($ready.generalEligibleDishCount); medical eligible $($ready.medicalEligibleDishCount)."
}

$cap = Read-Json 'Optimizer capabilities' "$OptimizerUrl/api/v1/internal/optimizer/capabilities"
if ($cap) { Add-Check 'Current optimizer code' ($cap.generalOptionalNutrition -eq $true) 'generalOptionalNutrition must be true; otherwise restart Meal Optimizer.' }

foreach ($allergy in @($false, $true)) {
    $label = $(if ($allergy) { 'General with egg + soy exclusions' } else { 'General candidates' })
    $tags = @()
    if ($allergy) { $tags = @('ALLERGEN_EGG', 'ALLERGEN_SOY') }
    $body = @{ planningMode = 'GENERAL_REFERENCE'; maxGiAllowed = 100; excludedMedicalTags = $tags; excludedRecentDishIds = @() }
    $candidates = Read-Json $label "$CatalogUrl/api/v1/internal/dishes/candidates" $body
    if ($null -ne $candidates) {
        $rows = @($candidates)
        $unexpected = @($rows | Where-Object { $_.code -notlike 'NIN_BULK_*' })
        Add-Check $label ($rows.Count -gt 0 -and $unexpected.Count -eq 0) "$($rows.Count) current source candidates; $($unexpected.Count) unexpected old/QA rows."
        Write-Host "`n$label - roles (coverage only, not proof of a feasible plan):"
        $rows | Group-Object role | Select-Object Name, Count | Format-Table -AutoSize
    }
}

try {
    $response = Invoke-WebRequest -Uri "$FrontendUrl/" -UseBasicParsing -TimeoutSec 15
    Add-Check 'Frontend' ($response.StatusCode -eq 200) $FrontendUrl
    $asset = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'frontend\public\food-images\nin-bulk-20261126') -File | Select-Object -First 1
    if ($asset) {
        $photo = Invoke-WebRequest -Uri "$FrontendUrl/food-images/nin-bulk-20261126/$($asset.Name)" -UseBasicParsing -TimeoutSec 15
        Add-Check 'Local food photo' ($photo.StatusCode -eq 200 -and $photo.Headers['Content-Type'] -like 'image/*') $asset.Name
    } else { Add-Check 'Local food photo' $false 'No exported bulk photos found.' }
} catch { Add-Check 'Frontend/photo' $false $_.Exception.Message }

Write-Host "`nDemo readiness - read-only checks; no accounts, profiles or meal plans were changed."
$checks | Format-Table -AutoSize -Wrap
Write-Host 'After PASS: log in, choose Pho thong / tham khao explicitly, keep your allergies, generate a day, regenerate with confirmation, then ask the chatbot about that plan.'
Write-Host 'A PASS here checks running versions, imported rows, candidates and photos. It does not certify successful meal generation or chatbot answers.'
if (@($checks | Where-Object Status -eq 'FAIL').Count -gt 0) { exit 1 }
exit 0

