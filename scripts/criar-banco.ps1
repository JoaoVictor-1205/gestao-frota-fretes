param(
    [string]$Servidor = 'localhost',
    [int]$Porta = 3306,
    [string]$Usuario = 'root',
    [string]$Mysql = 'mysql'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskMysqlExecutable = $null
$taskMysqlCommand = Get-Command $Mysql -CommandType Application -ErrorAction SilentlyContinue
if ($taskMysqlCommand) {
    $taskMysqlExecutable = $taskMysqlCommand.Source
} elseif ($Mysql -eq 'mysql') {
    foreach ($taskProgramRoot in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if (!$taskProgramRoot) { continue }
        $taskMysqlRoot = Join-Path $taskProgramRoot 'MySQL'
        if (!(Test-Path -LiteralPath $taskMysqlRoot)) { continue }
        $taskInstallations = Get-ChildItem -LiteralPath $taskMysqlRoot -Directory |
            Where-Object { $_.Name -like 'MySQL Server *' } | Sort-Object Name -Descending
        foreach ($taskInstallation in $taskInstallations) {
            $taskCandidate = Join-Path $taskInstallation.FullName 'bin\mysql.exe'
            if (Test-Path -LiteralPath $taskCandidate) {
                $taskMysqlExecutable = $taskCandidate
                break
            }
        }
        if ($taskMysqlExecutable) { break }
    }
}
if (!$taskMysqlExecutable) {
    throw 'mysql não encontrado. Instale o MySQL ou informe -Mysql com o caminho de mysql.exe.'
}

# A senha fica apenas no ambiente deste processo e dos clientes MySQL filhos.
# FROTA_DB_SENHA tem prioridade, como na aplicação; o arquivo é lido em UTF-8.
$taskPassword = [Environment]::GetEnvironmentVariable('FROTA_DB_SENHA', 'Process')
$taskHasPassword = $null -ne $taskPassword
$taskConfig = Join-Path $taskRoot 'config.properties'
if (!$taskHasPassword -and (Test-Path -LiteralPath $taskConfig)) {
    foreach ($taskLine in Get-Content -LiteralPath $taskConfig -Encoding UTF8) {
        if ($taskLine -match '^\s*db\.senha\s*[=:][ \t]*(.*)$') {
            $taskPassword = $Matches[1]
            $taskHasPassword = $true
        }
    }
}
if (!$taskHasPassword) {
    $taskSecurePassword = Read-Host "Senha MySQL para $Usuario" -AsSecureString
    $taskPassword = [System.Net.NetworkCredential]::new('', $taskSecurePassword).Password
}

$taskPreviousPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $taskPassword, 'Process')
    $taskMysqlArguments = @(
        "--host=$Servidor", "--port=$Porta", "--user=$Usuario",
        '--default-character-set=utf8mb4', '--connect-timeout=10'
    )
    # SOURCE faz o cliente ler o UTF-8 diretamente, sem conversão pelo pipeline PowerShell.
    $taskCreateDatabaseSql = (Join-Path $taskRoot 'database\00-criar-banco.sql').Replace('\', '/')
    & $taskMysqlExecutable @taskMysqlArguments "--execute=SOURCE $taskCreateDatabaseSql"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar/verificar o banco gestao_frota.' }
    $taskSchemaSql = (Join-Path $taskRoot 'database\01-schema.sql').Replace('\', '/')
    & $taskMysqlExecutable @taskMysqlArguments '--database=gestao_frota' "--execute=SOURCE $taskSchemaSql"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar/verificar as tabelas.' }
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $taskPreviousPassword, 'Process')
    $taskPassword = $null
    $taskSecurePassword = $null
}
Write-Host 'Banco MySQL gestao_frota pronto, com as cinco tabelas da atividade.'
