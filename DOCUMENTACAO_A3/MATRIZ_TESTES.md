# Matriz de testes — A3 Spring Petclinic

**Método:** os casos JUnit foram identificados diretamente nos cinco arquivos de testes QA. Os cenários HTTP provêm de `testes_api_http.py`. O status dos testes novos permanece **a executar** até serem produzidos relatórios locais.

| Grupo | IDs | Quantidade | Tipo | Objetivo |
|---|---|---:|---|---|
| Validação de proprietário | TA-01–04, TA-08–13 | 10 | JUnit + Jakarta Validation | Telefone e campos obrigatórios |
| Modelos e relacionamentos | TA-14–20 | 7 | JUnit | Pets, visitas e associação com proprietário |
| Redirecionamento Swagger | TA-05–06 | 2 | JUnit | Redirect e método HTTP GET |
| Segurança do workflow | TA-07 | 1 | JUnit desabilitado | Referências de GitHub Actions fixas por SHA |
| Controlador de proprietário | TA-21–31 | 11 | Mockito + JUnit | CRUD, filtro, status HTTP e propagação para serviço |
| Listagens HTTP | API-01–03 | 3 | HTTP local | Listar proprietários, pets e veterinários |
| Busca HTTP inexistente | API-04 | 1 | HTTP local | Resposta HTTP 404 |
| CRUD HTTP | API-05, API-06, API-08 | 3 | HTTP local | Cadastro, consulta e limpeza de cadastro de teste |
| Rejeições HTTP | API-07, API-09–10 | 3 | HTTP local | 4xx para telefone inválido, sobrenome numérico e telefone longo |
| **Total** | | **41** | | **31 JUnit + 10 HTTP** |

**Situação conhecida anterior:** a primeira execução com 20 JUnit teve 18 aprovados e 2 ignorados; os 8 HTTP originais tiveram 5 aprovados, 1 falha e 2 bloqueados, conforme evidências anexadas. **Não é correto apresentar os 41 casos como aprovados antes de executar esta versão.**
