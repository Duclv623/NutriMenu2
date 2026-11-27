param([switch]$Install, [switch]$CreateDatabases, [switch]$Build)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
$localPython = Join-Path $PSScriptRoot 'ai-service\.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $localPython)) {
    & py -3.12 -m venv (Join-Path $PSScriptRoot 'ai-service\.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.12 and retry.' }
    $Install = $true
}
if ($Install) {
    & $localPython -m pip install -r ai-service\requirements.lock.txt
    if ($LASTEXITCODE -ne 0) { throw 'Python dependency installation failed.' }
    & npm --prefix frontend ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
}
$bootstrapArgs = @('scripts\bootstrap_runtime.py')
if ($CreateDatabases) { $bootstrapArgs += '--create-databases' }
if ($Install) { $bootstrapArgs += '--prepare-tokenizer' }
& $localPython @bootstrapArgs
if ($LASTEXITCODE -ne 0) { throw 'Runtime configuration/database setup failed.' }
& $localPython scripts\verify_catalog.py
if ($LASTEXITCODE -ne 0) { throw 'Catalog/source verification failed.' }
if ($Build) {
    $repo = Join-Path $PSScriptRoot '.build-cache\maven'
    & mvn "-Dmaven.repo.local=$repo" -f BackendNutriMenu\pom.xml '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Java build failed. Check Java 21, Maven Central connectivity and the error above.' }
    & npm --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
}
Write-Host 'Setup finished. Use .\run-service.ps1 <Service> in separate terminals, or .\run-all.ps1.'
