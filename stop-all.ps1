$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $PSScriptRoot '.runtime\services.json'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No tracked launch.'; exit 0 }
$tracked = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
foreach ($entry in $tracked) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if (-not $process) { continue }
    if ($process.StartTime.ToUniversalTime().ToString('o') -ne $entry.started) { throw 'PID was reused; refusing to stop an unrelated process.' }
    # Terminate only the process tree created by run-all.ps1, never by port/name.
    & taskkill.exe /PID $entry.pid /T /F
}
Remove-Item -LiteralPath $pidFile
Write-Host 'Stopped the tracked service launch.'
