$ErrorActionPreference = 'Stop'
$taskRoot = Join-Path $env:USERPROFILE 'dev\review-system'
$taskTools = Get-Content -LiteralPath (Join-Path $taskRoot 'database-tools.json') -Raw | ConvertFrom-Json
$taskAdminConfig = Join-Path $taskRoot 'config\mysql-admin.cnf'
$taskClient = Join-Path $taskTools.mysql.path 'bin\mysqladmin.exe'
function Test-TaskDatabase {
    # Windows PowerShell converts native stderr into a terminating error under Stop.
    $taskPreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $taskClient "--defaults-extra-file=$taskAdminConfig" ping 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    } finally { $ErrorActionPreference = $taskPreviousPreference }
}
if (Test-TaskDatabase) { Write-Output 'MySQL is already running.'; exit 0 }
$taskSocket = New-Object System.Net.Sockets.TcpClient
try { $taskSocket.Connect('127.0.0.1',3306); throw 'Port 3306 is occupied by another process. Existing services are preserved.' } catch [System.Net.Sockets.SocketException] {} finally { $taskSocket.Dispose() }
$taskServer = Join-Path $taskTools.mysql.path 'bin\mysqld.exe'
$taskIni = Join-Path $taskRoot 'mysql.ini'
Start-Process -FilePath $taskServer -ArgumentList "--defaults-file=`"$taskIni`"" -WindowStyle Hidden | Out-Null
for ($taskAttempt=0; $taskAttempt -lt 30; $taskAttempt++) {
    Start-Sleep -Seconds 1
    if (Test-TaskDatabase) { Write-Output 'MySQL is ready at localhost:3306.'; exit 0 }
}
throw 'MySQL startup timed out. Inspect dev/review-system/mysql-logs/mysql.err.'
