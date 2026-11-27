param([switch]$LaunchAll)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$runtime = Join-Path $root '.runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$pidFile = Join-Path $runtime 'services.json'
if (Test-Path -LiteralPath $pidFile) {
    $tracked = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    foreach ($entry in $tracked) {
        if (Get-Process -Id $entry.pid -ErrorAction SilentlyContinue) {
            throw 'A previous launch is still running. Use stop-all.ps1 first.'
        }
    }
}
$ports = @{ users=8083; catalog=8081; optimizer=8082; gateway=8080; chatbot=8000; frontend=5173 }
foreach ($port in $ports.Values) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is occupied. Stop the previous application in its terminal before launching this checkout."
    }
}
$serviceScript = Join-Path $root 'run-service.ps1'
$escapedServiceScript = $serviceScript.Replace("'", "''")
$started = @()
foreach ($service in @('users','catalog','optimizer','gateway','chatbot','frontend')) {
    if ($LaunchAll) {
        $commandArg = ('"& ''' + $escapedServiceScript + ''' ' + $service + '"')
        $arguments = @('-NoExit','-NoProfile','-ExecutionPolicy','Bypass','-Command',$commandArg)
        $process = Start-Process powershell.exe -ArgumentList $arguments -WorkingDirectory $root -PassThru
    } else {
        $log = Join-Path $runtime "$service.log"
        $errorLog = Join-Path $runtime "$service-error.log"
        $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + $serviceScript + '"'),$service)
        $process = Start-Process powershell.exe -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $errorLog
    }
    $started += [pscustomobject]@{ service=$service; pid=$process.Id; started=$process.StartTime.ToUniversalTime().ToString('o'); port=$ports[$service] }
    $started | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8
}
if ($LaunchAll) {
    Write-Host 'Opened 6 service terminal windows (users, catalog, optimizer, gateway, chatbot, frontend).'
    Write-Host 'Wait for startup, then run .\check-demo.ps1 and open http://127.0.0.1:5173. Use .\stop-all.ps1 to close all windows.'
} else {
    Write-Host 'Services were launched. Logs: .runtime\<service>.log and <service>-error.log.'
    Write-Host 'Wait for startup, then run .\check-demo.ps1 and open http://127.0.0.1:5173.'
}
