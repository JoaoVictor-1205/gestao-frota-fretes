$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'java-tools.ps1')
if (!(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'lib') -Filter 'mysql-connector-j-*.jar' -ErrorAction SilentlyContinue)) {
    & (Join-Path $PSScriptRoot 'baixar-driver.ps1')
}
& (Join-Path $PSScriptRoot 'compilar.ps1') -Testes
Push-Location $taskRoot
try {
    # Lê URL/usuário/senha de Configuracao; cria e remove um banco temporário exclusivo.
    & $FrotaJava '-Dfile.encoding=UTF-8' "-Djava.io.tmpdir=$(Join-Path $taskRoot 'build\tmp')" -cp 'build\classes;build\test-classes;lib\*' tests.MySqlIntegracaoTest '--executar'
    if ($LASTEXITCODE -ne 0) { throw 'A integração com MySQL falhou.' }
} finally { Pop-Location }
