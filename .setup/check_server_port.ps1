$ErrorActionPreference = 'Stop'
$taskSocket = New-Object System.Net.Sockets.TcpClient
try {
    $taskSocket.Connect('127.0.0.1',8080)
    Write-Output 'Port 8080 is already in use. Stop the existing Tomcat before starting another server.'
    exit 1
} catch [System.Net.Sockets.SocketException] {
    exit 0
} finally { $taskSocket.Dispose() }
