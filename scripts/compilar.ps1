param([switch]$Testes)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'java-tools.ps1')
Push-Location $taskRoot
try {
    # Limpa somente as saídas geradas, conferindo o destino absoluto antes de remover.
    $taskBuild = [IO.Path]::GetFullPath((Join-Path $taskRoot 'build'))
    foreach ($taskDirectory in @('classes', 'test-classes')) {
        $taskOutput = [IO.Path]::GetFullPath((Join-Path $taskBuild $taskDirectory))
        if (!$taskOutput.StartsWith($taskBuild + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Destino de compilação inválido.' }
        if (Test-Path -LiteralPath $taskOutput) {
            $taskItem = Get-Item -LiteralPath $taskOutput -Force
            if ($taskItem.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'A pasta de compilação não pode ser um link.' }
            Remove-Item -LiteralPath $taskOutput -Recurse -Force
        }
    }
    New-Item -ItemType Directory -Path build\classes,build\tmp -Force | Out-Null
    $taskSources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' | ForEach-Object FullName)
    & $FrotaJavac --release 17 -encoding UTF-8 -d build\classes @taskSources
    if ($LASTEXITCODE -ne 0) { throw 'A compilação Java falhou.' }
    & $FrotaJar "-J-Djava.io.tmpdir=$(Join-Path $taskRoot 'build\tmp')" --create --file build\gestao-frota-fretes.jar --main-class app.Main -C build\classes .
    if ($LASTEXITCODE -ne 0) { throw 'A geração do JAR falhou.' }
    if ($Testes) {
        New-Item -ItemType Directory -Path build\test-classes -Force | Out-Null
        $taskTestSources = @(Get-ChildItem -LiteralPath tests -Recurse -Filter '*.java' | ForEach-Object FullName)
        & $FrotaJavac --release 17 -encoding UTF-8 -cp build\classes -d build\test-classes @taskTestSources
        if ($LASTEXITCODE -ne 0) { throw 'A compilação dos testes Java falhou.' }
    }
    Write-Host 'Compilação concluída: build\gestao-frota-fretes.jar'
} finally { Pop-Location }
