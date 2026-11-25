# Relatório técnico de Quality Assurance — Spring Petclinic REST

**Data de consolidação:** 25/11/2026  
**Projeto:** A3-QA-Spring-Petclinic  
**Repositório:** https://github.com/Adler088/A3-QA-Spring-Petclinic  
**Tecnologias:** Java, Spring Boot, JUnit 5, Mockito, Maven, H2, JaCoCo, Python e ferramentas de análise estática.

## 1. Objetivo

Avaliar validação de dados, comportamento dos controladores, endpoints REST, regressão de defeitos, qualidade estática e cobertura. Separar evidências realmente executadas de testes preparados para reteste.

## 2. Escopo e estratégia

A avaliação contém testes unitários/isolados de validação de entidades e controladores com Mockito; testes HTTP locais contra a API em `http://localhost:9966/petclinic/api`; inspeção estática com Checkstyle, SpotBugs e Semgrep; e cobertura com JaCoCo. O componente Angular e os fluxos completos via navegador não fazem parte dos cenários automatizados aqui fornecidos.

## 3. Evidências executadas antes da revisão

| Item | Resultado comprovado |
|---|---|
| Primeira bateria JUnit | 20 testes, 18 aprovados, 0 falhas, 0 erros, 2 ignorados |
| Maven + JaCoCo | BUILD SUCCESS na execução encaminhada em 25/11/2026 |
| Cobertura de linhas | 97 cobertas, 1.462 não cobertas: 6,22% |
| Cobertura de desvios | 17 cobertos, 355 não cobertos: 4,57% |
| Cobertura de instruções | 324 cobertas, 6.260 não cobertas: 4,92% |
| Cobertura de métodos | 38 cobertos, 346 não cobertos: 9,90% |
| Cobertura de classes | 8 cobertas, 60 não cobertas: 11,76% |
| HTTP local, 8 cenários | 5 aprovados, 1 falhou, 2 bloqueados |

A execução HTTP anterior está preservada em `DOCUMENTACAO_A3/EVIDENCIAS_ANTES_RETESTE`. A falha de API-05 veio de dados de teste incompatíveis com a regex de sobrenome de `openapi.yml`: o script acrescentava dígitos ao sobrenome. Isso impedia o cadastro e bloqueava os dois testes dependentes. A revisão corrige a massa de dados, mas o reteste ainda precisa ser executado; não se atribui aprovação antecipadamente.

## 4. Inspeção estática — linha de base anterior

| Ferramenta | Quantidade | Observação |
|---|---:|---|
| Checkstyle | 1.299 | Ocorrências de estilo e convenções; regra configurada `sun_checks.xml` |
| SpotBugs | 75 | Alertas estáticos; não significam 75 defeitos comprovados |
| Semgrep | 15 | Inclui workflows com tags mutáveis, hashes bcrypt em seeds e mapeamento HTTP genérico |
| SonarQube for IDE | Pendente | Não há relatório verificável incluído |

As quantidades são da análise anterior compartilhada na atividade, não de uma nova execução dentro deste pacote. A classificação e priorização devem considerar falsos positivos e relevância no perfil ativo do Spring.

## 5. Defeitos rastreados e decisões

| Ticket Jira | Descrição | Situação nesta versão |
|---|---|---|
| PQT-1 | Telefone de proprietário com 11 dígitos gerava erro de validação | Validação de domínio existente aceita 10 ou 11 dígitos; regressões JUnit incluídas |
| PQT-2 | Redirecionamento Swagger com `@RequestMapping` genérico | Corrigido para `@GetMapping` e teste de anotação habilitado |
| PQT-3 | GitHub Actions referenciadas por tags mutáveis | Pendente; teste de política permanece desabilitado e registra a dívida técnica |

Não foram introduzidas alterações de permissão, autenticação ou mudanças de comportamento não relacionadas aos cenários acima.

## 6. Evolução implementada

- Mantidos os 20 casos JUnit originais, agora com o teste PQT-2 habilitado.
- Adicionados 11 testes de comportamento do controlador de proprietários utilizando Mockito: consulta, filtro, listagem, cadastro, atualização e exclusão.
- Atualizada a massa de dados do cadastro HTTP para usar sobrenome válido, respeitando as restrições OpenAPI.
- Adicionados dois cenários HTTP negativos: sobrenome com números e telefone com 12 dígitos.
- Incluído gerador do relatório consolidado com resultados extraídos dos XMLs Surefire e evidências HTTP existentes.
- Padronizada a indentação da declaração de validação de telefone na entidade `Owner`.

**Bateria preparada no pacote:** 31 casos JUnit (30 habilitados e 1 desabilitado), além de 10 cenários HTTP. A bateria nova ainda não foi verificada na máquina de execução; esses números representam casos implementados, não aprovações.

## 7. Interpretação de qualidade e limitações

A linha de base de cobertura é baixa e representa somente os testes seletivos executados naquela ocasião. Cobertura não comprova ausência de defeitos; recomenda-se ampliar testes de integração, de autorização e de fluxo Angular. O erro de teste HTTP anterior é problema na massa de teste, não evidência de falha de cadastro do sistema. A versão corrigida precisa ser retestada.

O teste PQT-3 permanece pendente para não declarar como segura uma referência de GitHub Actions sem verificar e fixar os hashes SHA correspondentes. Nenhuma evidência de execução dos novos testes ou de redução dos totais das ferramentas de análise estática foi fabricada.

## 8. Critérios de decisão sobre a entrega

**Decisão provisória: aprovado com ressalvas para demonstração acadêmica, sujeito ao reteste.** Os testes iniciais comprovaram funcionamento das regras de validação e dos modelos, mas a cobertura de linhas era 6,22%, os fluxos críticos não estavam integralmente cobertos e ainda existe alerta de dependências de workflows. Antes de uma decisão de produção, exigir reteste completo, revisão de segurança e evidências de cobertura ampliada.

## 9. Evidências e rastreabilidade

- Evidências anteriores: `DOCUMENTACAO_A3/EVIDENCIAS_ANTES_RETESTE/`.
- Evidências HTTP após nova execução: `evidencias_api/resultado_api.json`, `.csv` e `RELATORIO_API.md`.
- JUnit após nova execução: `target/surefire-reports/`.
- Cobertura JaCoCo após nova execução: `target/site/jacoco/index.html`, `jacoco.xml` e `relatorio_cobertura.md`.
- Consolidação após nova execução: `DOCUMENTACAO_A3/RESULTADOS_EXECUCAO_ATUAL.md`.

## 10. Próximas ações objetivas

1. Executar `executar_tudo.ps1` no Windows com o backend ativo na porta 9966.
2. Conferir os resultados JUnit em Surefire; se houver falha, corrigir a causa e repetir.
3. Conferir os 10 cenários HTTP; em especial o cadastro e a limpeza de dados.
4. Gerar o relatório consolidado; registrar cobertura pós-alteração e comparar com a linha de base.
5. Reexecutar Checkstyle, SpotBugs e Semgrep para uma comparação metodologicamente válida.
6. Anexar prints do terminal, do JaCoCo e do Jira à entrega da professora.
