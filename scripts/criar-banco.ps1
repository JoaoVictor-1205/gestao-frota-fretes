param(
    [string]$Servidor = 'localhost',
    [int]$Porta = 5432,
    [string]$Usuario = 'postgres',
    [string]$Psql = 'psql'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (!(Get-Command $Psql -ErrorAction SilentlyContinue)) { throw 'psql não encontrado. Instale o PostgreSQL ou informe -Psql com o caminho do executável.' }
# A senha é solicitada pelo psql; não aparece em argumento ou arquivo do script.
& $Psql -h $Servidor -p $Porta -U $Usuario -d postgres -v ON_ERROR_STOP=1 -f (Join-Path $taskRoot 'database\00-criar-banco.sql')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar/verificar o banco gestao_frota.' }
& $Psql -h $Servidor -p $Porta -U $Usuario -d gestao_frota -v ON_ERROR_STOP=1 -f (Join-Path $taskRoot 'database\01-schema.sql')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar/verificar as tabelas.' }
Write-Host 'Banco gestao_frota pronto, com as cinco tabelas da atividade.'
