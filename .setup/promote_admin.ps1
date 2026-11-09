param([Parameter(Mandatory=$true)][string]$Username)
$ErrorActionPreference = 'Stop'
if ($Username -notmatch '^[a-zA-Z0-9_]{4,40}$') { throw 'Use a registered username with 4-40 letters, digits, or underscores.' }
$taskRoot = Join-Path $env:USERPROFILE 'dev\review-system'
$taskTools = Get-Content -LiteralPath (Join-Path $taskRoot 'database-tools.json') -Raw | ConvertFrom-Json
$taskMysql = Join-Path $taskTools.mysql.path 'bin\mysql.exe'
$taskAdminConfig = Join-Path $taskRoot 'config\mysql-admin.cnf'
$taskSql = "UPDATE app_user SET role='ADMIN' WHERE username='$Username'; SELECT ROW_COUNT();"
$taskResult = $taskSql | & $taskMysql "--defaults-extra-file=$taskAdminConfig" --database=review_system --batch --skip-column-names
if ($LASTEXITCODE -ne 0 -or $taskResult -ne '1') { throw 'A matching account could not be updated.' }
Write-Output 'Administrator role assigned. Log out and log in again to refresh permissions.'
