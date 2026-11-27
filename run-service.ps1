param([Parameter(Mandatory=$true)][ValidateSet('users','catalog','optimizer','gateway','chatbot','frontend')][string]$Service)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$ports = @{ users=8083; catalog=8081; optimizer=8082; gateway=8080; chatbot=8000; frontend=5173 }
try { $Host.UI.RawUI.WindowTitle = "NutriMenu2 - $Service ($($ports[$Service]))" } catch {}
Write-Host "=== NutriMenu2: $Service (port $($ports[$Service])) ===" -ForegroundColor Cyan
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
if ($Service -eq 'frontend') {
    Set-Location -LiteralPath (Join-Path $projectRoot 'frontend')
    & npm run dev
} elseif ($Service -eq 'chatbot') {
    $env:TIKTOKEN_CACHE_DIR = Join-Path $projectRoot '.runtime\tiktoken'
    Set-Location -LiteralPath (Join-Path $projectRoot 'ai-service')
    & .\run.ps1
} else {
    $modules = @{ users='Userservice'; catalog='foodcatalogservice'; optimizer='mealoptimizerservice'; gateway='gateway' }
    Set-Location -LiteralPath (Join-Path $projectRoot (Join-Path 'BackendNutriMenu' $modules[$Service]))
    $repo = Join-Path $projectRoot '.build-cache\maven'
    & mvn "-Dmaven.repo.local=$repo" spring-boot:run
}
exit $LASTEXITCODE
