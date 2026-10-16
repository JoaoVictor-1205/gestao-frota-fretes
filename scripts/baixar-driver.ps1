$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskLibrary = Join-Path $taskRoot 'lib'
$taskDriver = Join-Path $taskLibrary 'postgresql-42.7.14.jar'
New-Item -ItemType Directory -Path $taskLibrary -Force | Out-Null
if (Test-Path -LiteralPath $taskDriver) { Write-Host 'O driver JDBC já está disponível em lib/.'; return }
$taskTemporary = "$taskDriver.tmp"
try {
    Invoke-WebRequest -Uri 'https://jdbc.postgresql.org/download/postgresql-42.7.14.jar' -OutFile $taskTemporary
    Move-Item -LiteralPath $taskTemporary -Destination $taskDriver
    Write-Host 'Driver PostgreSQL baixado do site oficial para lib/.'
} finally {
    if (Test-Path -LiteralPath $taskTemporary) { Remove-Item -LiteralPath $taskTemporary }
}
