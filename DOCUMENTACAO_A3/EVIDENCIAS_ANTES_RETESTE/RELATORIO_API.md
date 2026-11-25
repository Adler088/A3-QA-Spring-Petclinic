# Evidencia de testes de API HTTP

- Base: `http://localhost:9966/petclinic/api`
- Casos: 8; aprovados: 5; falhas: 1; bloqueados: 2

| ID | Teste | Resultado | Observacoes |
|---|---|---|---|
| API-01 | Listagem de proprietarios responde sem erro 5xx | APROVADO | HTTP 200 |
| API-02 | Listagem de pets responde sem erro 5xx | APROVADO | HTTP 200 |
| API-03 | Listagem de veterinarios responde sem erro 5xx | APROVADO | HTTP 200 |
| API-04 | ID de proprietario inexistente retorna 404 | APROVADO | HTTP 404 |
| API-05 | Cadastrar proprietario com 11 digitos (HTTP 201) | FALHOU | AssertionError: Esperado HTTP 201, recebido 400; resposta={"detail":"The request contains invalid or missing parameters","instance":"/petclinic/api/owners","status":400,"title":"MethodArgumentNotValidException","type":"http://localhost:9966/petclinic/api/owners","timestamp":"20 |
| API-06 | Consultar proprietario recem-criado | BLOQUEADO | Dependencia: API-05 nao criou proprietario |
| API-07 | Telefone invalido nao deve causar erro 500 | APROVADO | HTTP 400 (esperado 4xx) |
| API-08 | Excluir proprietario criado pelo teste | BLOQUEADO | Dependencia: API-05 nao criou proprietario |
