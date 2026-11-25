$ErrorActionPreference = 'Stop'
if (!(Test-Path '.\pom.xml') -or !(Test-Path '.\mvnw.cmd')) {
    throw 'Abra o PowerShell na raiz A3-QA-Spring-Petclinic (junto do pom.xml).'
}
$java21 = 'C:\Program Files\Java\jdk-21.0.12'
if (Test-Path "$java21\bin\java.exe") {
    $env:JAVA_HOME = $java21
    $env:Path = "$java21\bin;$env:Path"
}
Write-Host 'ETAPA A - JUnit + JaCoCo (31 testes definidos; 1 desabilitado)' -ForegroundColor Cyan
& .\mvnw.cmd '-Dtest=OwnerValidationTest,ModelRelationsQaTest,RootRestControllerV1QaTest,GitHubActionsQaTest,OwnerControllerExtraQaTest' test jacoco:report
$codigoMaven = $LASTEXITCODE
if ($codigoMaven -ne 0) { Write-Warning "Maven falhou (codigo $codigoMaven). Corrija antes de afirmar que os novos testes passaram." }
if ($codigoMaven -eq 0 -and (Test-Path '.\target\site\jacoco\jacoco.xml')) {
    py -3.13 .\cobertura_jacoco.py
}
Write-Host 'ETAPA B - API HTTP (backend deve estar executando em outra janela)' -ForegroundColor Cyan
py -3.13 .\testes_api_http.py
if ($LASTEXITCODE -ne 0) { Write-Warning 'Testes HTTP incompletos ou com falhas. Consulte evidencias_api.' }
Write-Host 'Relatorios: target\surefire-reports; target\site\jacoco; evidencias_api; relatorio_cobertura.md'

py -3.13 .\gerar_relatorio_execucao.py
