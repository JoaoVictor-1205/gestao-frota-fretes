$ErrorActionPreference = 'Stop'
# O JAVA_HOME tem prioridade. Evita os atalhos Oracle sem JDK funcional no PATH.
$taskJdk = $null
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $taskJdk = $env:JAVA_HOME
} else {
    $taskCandidates = @(
        'C:\Program Files\Java\jdk-17',
        'C:\Program Files\Java\jdk-21'
    )
    foreach ($taskCandidate in $taskCandidates) {
        if (Test-Path -LiteralPath (Join-Path $taskCandidate 'bin\javac.exe')) { $taskJdk = $taskCandidate; break }
    }
}
if ($taskJdk) {
    $script:FrotaJava = Join-Path $taskJdk 'bin\java.exe'
    $script:FrotaJavac = Join-Path $taskJdk 'bin\javac.exe'
    $script:FrotaJar = Join-Path $taskJdk 'bin\jar.exe'
} else {
    $script:FrotaJava = (Get-Command java -ErrorAction Stop).Source
    $script:FrotaJavac = (Get-Command javac -ErrorAction Stop).Source
    $script:FrotaJar = (Get-Command jar -ErrorAction Stop).Source
}
