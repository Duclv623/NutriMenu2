param([switch]$AllJava, [switch]$WithDatabases, [switch]$RebuildCorpus, [switch]$Offline)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
$repo = Join-Path $PSScriptRoot '.build-cache\maven'
$modules = @('foodcatalogservice','mealoptimizerservice')
if ($AllJava) { $modules = @('') }
foreach ($module in $modules) {
    $pom = Join-Path $PSScriptRoot 'BackendNutriMenu\pom.xml'
    if ($module) { $pom = Join-Path $PSScriptRoot "BackendNutriMenu\$module\pom.xml" }
    $arguments = @("-Dmaven.repo.local=$repo",'-f',$pom,'test')
    if ($Offline) { $arguments += '-o' }
    if ($WithDatabases) { $arguments += '-Pintegration-tests' }
    & mvn @arguments
    if ($LASTEXITCODE -ne 0) { throw "Java tests failed: $module" }
}
& npm --prefix frontend test
if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
& npm --prefix frontend run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
$python = Join-Path $PSScriptRoot 'ai-service\.venv\Scripts\python.exe'
& $python scripts\verify_catalog.py
if ($LASTEXITCODE -ne 0) { throw 'Catalog/source hash verification failed.' }
& $python -m unittest discover -s BackendNutriMenu\foodcatalogservice\scripts\tests
if ($LASTEXITCODE -ne 0) { throw 'Catalog collector/builder tests failed.' }
$nodes = Get-Content ai-service\tests\structured_unit_nodes.json | ConvertFrom-Json
$pytestArgs = @('-m','pytest','-q')
if (-not $RebuildCorpus) { $pytestArgs += '--published-corpus' }
$pytestArgs += $nodes
$pytestArgs += 'tests/test_java_jwt_bridge.py'
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD = '1'
$env:PSYCOPG_IMPL = 'python'
$env:TIKTOKEN_CACHE_DIR = Join-Path $PSScriptRoot '.runtime\tiktoken'
Push-Location ai-service
try {
    & $python @pytestArgs
    if ($LASTEXITCODE -ne 0) { throw 'Structured RAG/JWT tests failed.' }
} finally { Pop-Location }
Write-Host 'Selected unit/native/contract checks passed. Use check-demo.ps1 and the browser acceptance flow to validate running services.'
