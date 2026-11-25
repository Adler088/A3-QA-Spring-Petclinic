# Execução em Windows — projeto A3

O código-fonte foi organizado para ser aberto diretamente na pasta que contém `pom.xml`. Não copie arquivos de `target` ou `.git` sobre seu repositório existente.

## 1. Backend

No PowerShell A, dentro do projeto, execute:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

Verifique `http://localhost:9966/petclinic/api/owners`. Caso a porta 9966 já esteja ocupada mas a API responda com JSON, não inicie outro servidor.

## 2. Testes completos

No PowerShell B, dentro do projeto, execute:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\executar_tudo.ps1"
```

O script seleciona os 31 testes QA JUnit, gera cobertura JaCoCo e roda os 10 testes HTTP usando Python 3.13. O projeto precisa manter o Maven wrapper e o Java 21 instalados.

## 3. Consolidar evidências

```powershell
py -3.13 .\gerar_relatorio_execucao.py
```

Confirme os arquivos gerados em `DOCUMENTACAO_A3/RESULTADOS_EXECUCAO_ATUAL.md`, `evidencias_api` e `target/site/jacoco`.

## 4. Segurança e dados

Os testes HTTP só se conectam a `localhost:9966`. Eles criam um proprietário fictício e solicitam sua remoção. Execute em ambiente de desenvolvimento, não em produção.

## 5. Envio ao GitHub

Compare as alterações antes de enviar. O ZIP é uma nova versão do projeto e não inclui histórico `.git` nem arquivos compilados `target`. Para manter o histórico atual, copie apenas os arquivos alterados para seu clone existente, revise com `git diff` e faça commit.
