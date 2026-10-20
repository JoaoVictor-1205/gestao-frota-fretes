$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskLibrary = Join-Path $taskRoot 'lib'
$taskDriver = Join-Path $taskLibrary 'mysql-connector-j-9.2.0.jar'
New-Item -ItemType Directory -Path $taskLibrary -Force | Out-Null
if (Test-Path -LiteralPath $taskDriver) { Write-Host 'O driver JDBC MySQL já está disponível em lib/.'; return }
$taskTemporary = "$taskDriver.tmp"
try {
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar' -OutFile $taskTemporary -TimeoutSec 120
    Move-Item -LiteralPath $taskTemporary -Destination $taskDriver
    Write-Host 'Driver MySQL Connector/J baixado do Maven Central para lib/.'
} finally {
    if (Test-Path -LiteralPath $taskTemporary) { Remove-Item -LiteralPath $taskTemporary }
}
