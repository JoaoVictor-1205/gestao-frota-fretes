param([switch]$Demo)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'java-tools.ps1')
& (Join-Path $PSScriptRoot 'compilar.ps1')
Push-Location $taskRoot
try {
    $taskArguments = @()
    if ($Demo) { $taskArguments += '--demo' }
    & $FrotaJava '-Dfile.encoding=UTF-8' -cp 'build\classes;lib\*' app.Main @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'A aplicação foi encerrada com erro.' }
} finally { Pop-Location }
