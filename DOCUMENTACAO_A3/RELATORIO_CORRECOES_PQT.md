# Relatório de correções PQT-1 a PQT-12 — spring-petclinic-rest

- **Data:** 26/11/2026
- **Base analisada:** dump `spring-petclinic-rest` recebido em 25/11/2026 (Spring Boot 4.1.1, Java 17/21, Maven 3.9).
- **Validação final:** `mvn clean verify` → **BUILD SUCCESS**, 384 testes, 0 falhas, 0 erros, 0 ignorados; JaCoCo check e gate do SpotBugs aprovados.

Todos os números "antes" foram medidos no dump recebido, com as mesmas ferramentas e a mesma configuração usadas no "depois". Os números do `RELATORIO_QA_A3.md` (1.299 / 75 / 15) vêm de uma análise anterior e aparecem só como referência.

## 1. Diagnóstico inicial

| Verificação | Resultado no dump | Ação |
|---|---|---|
| Build (`mvn clean verify`) | BUILD SUCCESS, 270 testes (269 ok, 1 ignorado: teste PQT-3 com `@Disabled`) | — |
| Contexto Spring com `petclinic.security.enable=false` | **Não subia**: `NoSuchBeanDefinitionException: PasswordEncoder`. O único bean `passwordEncoder` ficava dentro de `BasicAuthenticationConfig` (condicional), mas `UserServiceImpl` sempre precisa dele | Bean movido para `PasswordEncoderConfig` (incondicional); `BasicAuthenticationConfig` recebe o encoder por parâmetro. Teste: `SecurityDisabledContextTests` (3 testes: falham no dump, passam agora) |
| Beans `passwordEncoder` duplicados | Não havia duplicidade; o problema era o bean ausente no perfil sem segurança | Um único bean, sem `allow-bean-definition-overriding` |

## 2. Tabela de status

| Ticket | Status | Causa raiz | Correção | Arquivos principais |
|---|---|---|---|---|
| **PQT-1** | Concluído | O contrato da API (`openapi.yml`: 1–20 caracteres, `^[0-9]*$`) divergia do domínio (`Owner`: `^[0-9]{10,11}$`). No dump, 11 dígitos já retornava 201 no perfil padrão (o relatório A3 registra a correção da entidade). Faltava uma regra única: valores aceitos pelo contrato só eram recusados na persistência, e no PUT a violação aparecia no commit e virava HTTP 500. As colunas (`VARCHAR(20)`/`TEXT`) já comportavam 11 dígitos | Regra única de 10 a 11 dígitos (DDD + número fixo ou celular) no OpenAPI (DTO gerado com `@Pattern`, `@Size(min=10,max=11)`, `@NotNull` e mensagem clara) e na entidade (constantes `TELEPHONE_REGEXP`/`TELEPHONE_MESSAGE`). Sem mudança de DDL | `openapi.yml`, `model/Owner.java`; testes `OwnerTelephoneApiIntegrationTests`, `OwnerTelephoneContractTests`, `AbstractClinicServiceTests` |
| **PQT-4** | Concluído | (1) O DTO aceitava tamanhos inválidos, então a validação só ocorria no Hibernate. (2) No PUT, a violação chegava embrulhada em `TransactionSystemException` e caía no `@ExceptionHandler(Exception.class)` genérico, virando 500. (3) O mesmo handler genérico transformava 405/404/415 do Spring MVC em 500 | Validação na borda (DTO). `ExceptionControllerAdvice` reescrito: `ConstraintViolationException`, `TransactionSystemException` com causa de validação, `HandlerMethodValidationException` e `MethodArgumentNotValidException` retornam 400. Cada caso devolve `ProblemDetail` com `schemaValidationErrors` (`field`, `rejectedValue`, `defaultMessage`). Exceções `ErrorResponse` mantêm status e cabeçalhos, `AccessDeniedException` vira 403 e só erro inesperado vira 500, com mensagem genérica | `rest/advice/ExceptionControllerAdvice.java`, `openapi.yml`; testes `OwnerTelephoneApiIntegrationTests` (31), `ExceptionControllerAdviceTests` (15), `ApiHttpStatusIntegrationTests` (15) |
| **PQT-2** | Concluído | O dump já tinha `@GetMapping("/")` no redirect, e o Spring MVC respondia 405 para os outros métodos. Mas o handler genérico do advice convertia `HttpRequestMethodNotSupportedException` em **500** e descartava o cabeçalho `Allow` | Handler de `ErrorResponse` preserva status e cabeçalhos. POST/PUT/DELETE/PATCH em `/` agora retornam **405 + `Allow: GET`**, e GET continua com 302 para `/swagger-ui/index.html` | `ExceptionControllerAdvice.java`, `RootRestControllerV1.java` (formatação/Javadoc); testes `ApiHttpStatusIntegrationTests`, `ExceptionControllerAdviceTests` |
| **PQT-3** | Concluído | Os workflows usavam tags mutáveis (`actions/checkout@v3/@v4`, `setup-java@v3/@v4`, `upload-artifact@v4`). Os dois actions Docker já usavam SHA, mas sem o comentário de versão | As 11 referências `uses:` foram fixadas em SHA completo de 40 caracteres com `# vX.Y.Z`. Cada SHA foi conferido com `git ls-remote` no repositório oficial: checkout `3d3c42e… # v7.0.1`, setup-java `d1374f8… # v6.3.0`, upload-artifact `cf430e0… # v7.0.2`, setup-buildx `f87e599… # v4.4.1`, login-action `8aa5831… # v4.8.0` (todos em Node 24; os inputs usados existem nessas versões). O teste de política foi reabilitado | `.github/workflows/*.yml` (4), `qa/GitHubActionsQaTest.java` |
| **PQT-5** | Concluído | `EI_EXPOSE_REP/REP2`: o único caso real em código próprio era `User.getRoles/setRoles`, que expunha o `Set` interno. Os demais são estruturais | `setRoles` faz cópia defensiva e `getRoles` devolve uma visão somente leitura. Hibernate usa acesso por campo e o MapStruct usa o setter. As associações JPA `@ManyToOne` não podem ser copiadas sem destacar a entidade do contexto de persistência, então ficaram documentadas no filtro (item 6) | `model/User.java`, `spotbugs-exclude.xml`; teste `EntityEncapsulationTests` |
| **PQT-6** | Concluído | O `CallMonitoringAspect` (singleton, exposto via JMX) tinha `enabled`, `callCount` e `accumulatedCallTime` como campos comuns com sincronização parcial: `IS2_INCONSISTENT_SYNC` ×2, `AT_STALE_THREAD_WRITE_OF_PRIMITIVE` ×2, `AT_NONATOMIC_64BIT_PRIMITIVE` ×1 | `volatile boolean enabled` e contadores `LongAdder` (sem lock no caminho quente). A contabilização ocorre no `finally` | `util/CallMonitoringAspect.java`; teste `CallMonitoringAspectTests` (concorrência com várias threads, reset, desabilitado) |
| **PQT-7** | Concluído | `JdbcPetRepositoryImpl.findById` desembrulhava `owner_id` (`Integer`) para `int`, gerando NPE quando a coluna é NULL (`owner_id` é anulável no schema PostgreSQL). `createPetParameterSource` também gerava NPE sem dono ou tipo (`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`) | Pet sem dono é carregado com tipo e visitas, e um id inexistente lança `ObjectRetrievalFailureException` (o serviço devolve `null`, e a API responde 404). Parâmetros de insert/update são null-safe e a busca de dono foi unificada (`findOwnerOf`). `JdbcVisitRepositoryImpl` usa `getObject(..., LocalDate.class)` e não falha sem dono | `repository/jdbc/JdbcPetRepositoryImpl.java`, `JdbcVisitRepositoryImpl.java`; testes `JdbcPetRepositoryImplTests` (9; **7 falham com NPE no dump**), `AbstractClinicServiceTests.shouldReturnNullForUnknownPet` |
| **PQT-8** | Concluído | `Roles` declarava as constantes de papel como campos de instância (`SS_SHOULD_BE_STATIC` ×3) e não havia filtro de exclusão documentado | Constantes `public static final`. O bean `@Component Roles` foi mantido porque `@PreAuthorize("hasRole(@roles.OWNER_ADMIN)")` continua lendo os valores, coberto por teste 403. Criado o `spotbugs-exclude.xml`, comentado regra a regra e ligado ao build (`excludeFilterFile`), com o gate `spotbugs:check` na fase `verify`: um bug novo derruba o build (provado com uma classe de teste: `ES_COMPARING_PARAMETER_STRING_WITH_EQ` → BUILD FAILURE). Correções extras: `SIC_INNER_SHOULD_BE_STATIC_ANON` ×2, `MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR`, `DM_CONVERT_CASE` | `security/Roles.java`, `spotbugs-exclude.xml`, `pom.xml`, `JdbcVetRepositoryImpl.java`, `BindingErrorsResponse.java`, `Owner.java`; teste `RolesTests` |
| **PQT-9** | Concluído | O Semgrep (`detected-bcrypt-hash`) aponta o hash do usuário de seed nos 4 `data.sql`. É o BCrypt (custo 10) da credencial de demonstração pública `admin/admin`: não é segredo, e removê-lo quebraria o login de demonstração e os testes de autenticação | Supressão pontual e auditável: `nosemgrep: generic.secrets.security.detected-bcrypt-hash.detected-bcrypt-hash` só na linha do INSERT (no PostgreSQL, na linha anterior), com comentário explicando o motivo e o alerta de troca fora de dev. Os dados não mudaram. Testes confirmam que `admin/admin` autentica (200) e que senha errada recebe 401 | `src/main/resources/db/{h2,hsqldb,mysql,postgres}/data.sql`; testes em `ApiHttpStatusIntegrationTests` |
| **PQT-10** | Concluído | `dependabot.yml` sem `cooldown`: versões recém-publicadas (possivelmente comprometidas ou retiradas) viravam PR imediatamente | `cooldown.default-days: 7` para `maven` e `github-actions`; o ecossistema `github-actions` também mantém os SHAs do PQT-3. Arquivo validado contra o schema oficial (`check-jsonschema --builtin-schema vendor.dependabot`) | `.github/dependabot.yml` |
| **PQT-11** | Parcial | 1.311 ocorrências `sun_checks`, concentradas em convenções (linhas com mais de 80 colunas, `final` em parâmetros, Javadoc) e em regras que conflitam com Spring/JPA | Refatorações seguras e de alto impacto: **1.311 → 1.072 (−239; −18,2%)**, 17 regras zeradas (seção 5). O restante é dívida de convenção ou conflito com o framework e exige decisão do time sobre uma configuração ajustada, não edição em massa | 85 arquivos `.java` de `src/main` (formatação, imports, Javadoc, `package-info.java` ×13), `application.properties`, `pom.xml` |
| **PQT-12** | Concluído | Cobertura concentrada nos caminhos felizes. Os fluxos de erro (advice), o JDBC com nulos, a concorrência e a segurança desabilitada não tinham teste | +114 testes (270 → 384), 0 ignorados. Linhas **85,26% → 91,71%**, branches **67,74% → 77,21%**. Testes obrigatórios: PQT-1, PQT-4, PQT-2 e PQT-7 (colunas acima) | 10 classes de teste novas e 3 ampliadas em `src/test` |

## 3. Métricas antes × depois

| Métrica | Antes (dump) | Depois |
|---|---:|---:|
| `mvn clean verify` | BUILD SUCCESS | BUILD SUCCESS (agora com gate SpotBugs) |
| Testes executados | 270 (269 ok, 1 ignorado) | **384** (384 ok, 0 ignorados) |
| Falhas / erros | 0 / 0 | 0 / 0 |
| SpotBugs 4.10.5, effort Max, prioridade Medium (gate) | **75** | **0** |
| SpotBugs Medium + Low | 83 | 0 |
| SpotBugs sem o filtro de exclusão | 83 | 68 (todos estruturais, ver seção 6) |
| Checkstyle `sun_checks` (código de `src/main` + resources) | **1.311** | **1.072** (−18,2%) |
| Semgrep (regras equivalentes às do registry) | **14** | **0** (4 supressões auditadas) |
| JaCoCo linhas | 85,26% | **91,71%** |
| JaCoCo branches | 67,74% | **77,21%** |
| JaCoCo instruções | 85,36% | 92,50% |
| JaCoCo métodos | 90,51% | 96,03% |
| JaCoCo classes | 97,06% | 100,00% |

Diferenças em relação ao relatório A3 anterior: o Checkstyle dava 1.311 contra 1.299, com a mesma regra `sun_checks.xml` em execução nova. No Semgrep, o 15º achado (mapeamento HTTP genérico do PQT-2) já não existia no dump, porque o método usava `@GetMapping`.

## 4. Evidência de comportamento HTTP (mesma sonda no dump e na versão corrigida)

Pilha completa (filtros do Spring Security, Spring MVC e advice) com usuário administrador, perfil padrão `h2,spring-data-jpa`.

| Requisição | Antes | Depois |
|---|---:|---:|
| POST `/api/owners`, telefone com 11 dígitos | 201 | 201 |
| PUT `/api/owners/1`, telefone com 11 dígitos | 204 | 204 |
| POST `/api/owners`, 12 dígitos | 400 | 400 (agora barrado no DTO) |
| **PUT `/api/owners/1`, 12 dígitos** | **500** | **400** |
| POST com máscara `(11) 98765-4321` / 20 dígitos / JSON malformado | 400 | 400 |
| GET `/` | 302 | 302 → `/swagger-ui/index.html` |
| **POST / PUT / DELETE / PATCH `/`** | **500** | **405** + `Allow: GET` |
| **DELETE `/api/owners`** (método não mapeado) | **500** | **405** + `Allow: POST, GET` |
| **GET `/api/rota-inexistente`** | **500** | **404** |
| **POST `/api/owners` com `text/plain`** | **500** | **415** |
| GET `/api/owners/-1` / GET `/api/v2/owners?size=500` | 400 | 400 (campo `ownerId` / `size` identificado) |
| Contexto com `petclinic.security.enable=false` | não sobe | sobe (3 testes) |
| `JdbcPetRepositoryImplTests` (pet com `owner_id` NULL) | 7 de 9 com NPE | 9 de 9 ok |

## 5. Checkstyle — classificação antes × depois

| Regra | Antes | Depois | Classificação |
|---|---:|---:|---|
| LineLength (80 col.) | 317 | 266 | Convenção. Corrigido nos arquivos tocados; com limite de 120 colunas restariam 26 |
| FinalParameters | 260 | 248 | Convenção (sem efeito em runtime). Corrigido no código novo |
| DesignForExtension | 226 | 213 | **Conflito com o framework**: tornar métodos `final` quebra proxies CGLIB (`@Transactional`, `@PreAuthorize`) e proxies do Hibernate. Não aplicar |
| MissingJavadocMethod | 192 | 184 | Documentação. Javadoc adicionado no código novo e no alterado |
| JavadocVariable | 95 | 89 | Documentação |
| HiddenField | 63 | 63 | Idioma Java/Spring (parâmetro de construtor ou setter com o nome do campo). Falso positivo prático |
| Translation | 8 | 8 | Falso positivo: `messages_en.properties` é vazio de propósito, porque `messages.properties` já é o padrão em inglês |
| HideUtilityClassConstructor | 0 | 1 | Decisão: `Roles` precisa continuar instanciável como bean para o SpEL `@roles` |
| FileTabCharacter | 29 | 0 | Corrigido |
| JavadocStyle | 21 | 0 | Corrigido |
| AvoidStarImport | 15 | 0 | Corrigido (imports explícitos) |
| RegexpSingleline (espaço no fim da linha) | 14 | 0 | Corrigido |
| WhitespaceAround / WhitespaceAfter / NoWhitespaceBefore / ParenPad | 25 | 0 | Corrigido |
| JavadocPackage | 13 | 0 | Corrigido (13 `package-info.java`) |
| JavadocMethod | 8 | 0 | Corrigido |
| VisibilityModifier | 7 | 0 | Corrigido (campos `private` com acessores) |
| UnusedImports | 6 | 0 | Corrigido |
| MemberName / ConstantName | 4 | 0 | Corrigido |
| MagicNumber | 3 | 0 | Corrigido (constantes nomeadas) |
| OperatorWrap / NeedBraces | 5 | 0 | Corrigido |
| **Total** | **1.311** | **1.072** | **−239** |

Projeção (estimada a partir do relatório, não executada): com uma configuração ajustada ao Spring (LineLength 120 e sem DesignForExtension, FinalParameters e HiddenField), o total cairia para cerca de 308, quase só Javadoc.

## 6. SpotBugs — o que foi corrigido e o que foi excluído

Corrigidos no código: 15 achados (SS ×3, IS2 ×2, AT ×3, SIC ×2, NP ×1, MC ×1, DM ×1, EI ×2 de `User.roles`).

Excluídos em `spotbugs-exclude.xml`: cada regra combina padrão de bug com pacote ou classe e traz justificativa no próprio arquivo. São os 68 achados restantes sem filtro:

| Grupo | Qtde. | Justificativa |
|---|---:|---|
| Código gerado (`rest.dto`, `rest.api`, openapi-generator) | 41 | Regerado a cada build; não pode ser editado |
| `EI_EXPOSE_REP2` em construtores de controllers, serviços e repositórios | 16 | Injeção de beans singleton compartilhados de propósito; não podem ser copiados |
| Associações JPA `@ManyToOne` (`Pet.owner/type`, `Visit.pet`, `Role.user`) | 8 | O Hibernate depende da identidade da entidade; copiar destacaria o grafo. As coleções são protegidas com visões somente leitura |
| `THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION` na configuração do Spring Security | 3 | A API `HttpSecurity#build()` declara `throws Exception` |

Nenhuma regra foi desativada globalmente. O gate usa `threshold=Medium`, e `-Dspotbugs.threshold=Low` lista também os de baixa prioridade (0 hoje).

## 7. Semgrep — auditoria

| Regra | Antes | Depois | Tratamento |
|---|---:|---:|---|
| `action-not-pinned-to-commit-sha` | 9 | 0 | Corrigido (PQT-3) |
| `dependabot-missing-cooldown` | 1 | 0 | Corrigido (PQT-10) |
| `detected-bcrypt-hash` | 4 | 0 | Supressão por linha com justificativa (PQT-9) |

As 4 supressões usam o ID completo da regra do registry, ficam só na linha do hash e explicam o motivo no comentário SQL logo acima. Para auditar: `git grep -n nosemgrep`.

## 8. Pendências e decisões

1. **Front-end Angular** (`owner-add`/`owner-edit`) ainda aceita de 1 a 20 dígitos (`minlength="1" maxlength="20" pattern="^[0-9]*$"`). Para alinhar: `minlength="10" maxlength="11" pattern="^[0-9]{10,11}$"`. A API já recusa esses valores com 400.
2. **Coleção Postman/Newman e massa JMeter** não vieram no dump. Verifique se os telefones têm 10 ou 11 dígitos, senão o `newman-pipeline` vai falhar.
3. **Formato do telefone:** a API aceita apenas dígitos (sem máscara e sem +55). Se o negócio quiser aceitar máscara, normalize no front ou remova os não dígitos antes da validação. É decisão de produto.
4. **Atualização de versão maior nos workflows:** checkout v7, setup-java v6 e upload-artifact v7 (runtime Node 24), e os actions Docker de v3 para v4. Rode os workflows uma vez em uma branch. Para manter os Docker na v3: `docker/setup-buildx-action@e468171a9de216ec08956ac3ada2f0791b6bd435 # v3.11.1` e `docker/login-action@5e57cd118135c172c3672efd75eb46360885c0ef # v3.6.0`.
5. **Checkstyle (1.072 restantes):** decidir se o time adota uma configuração ajustada (seção 5) ou mantém `sun_checks` como relatório informativo (`failOnViolation=false`, como está).
6. **Limites do JaCoCo:** o POM exige linhas ≥ 85% e branches ≥ 66%. Recomenda-se subir para 90% e 75% para travar o ganho.
7. **AppSec fora do escopo dos tickets:** o `UserDto` devolve o campo `password` (hash) nas respostas de `/api/users`. Recomenda-se `writeOnly: true` no `openapi.yml`.
8. **Não executado aqui:** MySQL e PostgreSQL reais (só H2 e HSQLDB rodam nos testes; nesses `data.sql` a mudança é só de comentário) e os workflows do GitHub Actions.
9. **Limitações do dump:** `mvnw`, scripts `.py`/`.ps1` e arquivos `.json` não vieram. O `messages_de.properties` veio com caracteres corrompidos (U+FFFD) e **não foi alterado nem incluído no pacote**; mantenha o seu.
10. Os documentos A3 anteriores (`RELATORIO_QA_A3.md`, `RESULTADOS_EXECUCAO_ATUAL.md`, `MATRIZ_TESTES.md`) refletem o estado antigo (por exemplo, PQT-3 "Pendente"). Este relatório os substitui.

## 9. Como reproduzir

```bash
mvn clean verify                                   # testes + JaCoCo check + gate SpotBugs
mvn checkstyle:checkstyle                          # target/checkstyle-result.xml
mvn spotbugs:spotbugs -Dspotbugs.threshold=Low     # inclui achados de baixa prioridade
semgrep scan --config auto --exclude target .      # o CI pode usar o próprio ruleset
```
