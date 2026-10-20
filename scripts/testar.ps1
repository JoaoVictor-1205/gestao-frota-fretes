$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'java-tools.ps1')
& (Join-Path $PSScriptRoot 'compilar.ps1') -Testes
Push-Location $taskRoot
try {
    foreach ($taskClass in @('tests.RegraNegocioTest', 'tests.PersistenciaTest', 'tests.MascarasTest', 'tests.InterfaceTest')) {
        & $FrotaJava '-Dfile.encoding=UTF-8' '-Djava.awt.headless=true' "-Djava.io.tmpdir=$(Join-Path $taskRoot 'build\tmp')" -cp 'build\classes;build\test-classes' $taskClass
        if ($LASTEXITCODE -ne 0) { throw "Falha em $taskClass" }
    }
} finally { Pop-Location }
