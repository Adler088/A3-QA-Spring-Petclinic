"""Testes HTTP da API REST; executados APENAS contra localhost.

Comando: py -3.13 testes_api_http.py
Gera evidencias reais em evidencias_api/ (JSON, CSV e Markdown).
Nao inventa aprovacoes: falhas sao registradas e resultam em exit code 1.
"""
from __future__ import annotations
import csv, json, sys, time, uuid, urllib.request, urllib.error
from pathlib import Path

BASE = 'http://localhost:9966/petclinic/api'
OUT = Path('evidencias_api')
OUT.mkdir(exist_ok=True)
RESULTADOS = []


def requisicao(method, endpoint, data=None):
    assert endpoint.startswith('/') and not endpoint.startswith('//')
    url = BASE + endpoint
    body = json.dumps(data).encode('utf-8') if data is not None else None
    req = urllib.request.Request(url, data=body, method=method,
                                 headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode('utf-8', errors='replace')
            return resp.status, raw
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', errors='replace')


def teste(id, titulo, verificacao):
    inicio = time.perf_counter()
    try:
        detalhes = verificacao()
        estado, nota = 'APROVADO', detalhes or 'OK'
    except Exception as e:
        estado, nota = 'FALHOU', f'{type(e).__name__}: {e}'
    RESULTADOS.append({'id':id, 'teste':titulo, 'status':estado,
                       'detalhes':str(nota)[:450], 'duracao_s':round(time.perf_counter()-inicio, 3)})
    print(f'{id} | {estado:8} | {titulo}: {nota}')


def exigir_status(status, esperado, corpo):
    assert status == esperado, f'Esperado HTTP {esperado}, recebido {status}; resposta={corpo[:220]}'


def main():
    print('Alvo:', BASE, ' | testes SOMENTE em localhost')
    try:
        s, body = requisicao('GET', '/owners')
    except Exception as e:
        print(f'API indisponivel: inicialize o backend na porta 9966. Erro: {e}')
        return 2

    teste('API-01', 'Listagem de proprietarios responde sem erro 5xx',
          lambda: (lambda s,b: (assert_no_5xx(s,b), f'HTTP {s}')[1])(*requisicao('GET','/owners')))
    teste('API-02', 'Listagem de pets responde sem erro 5xx',
          lambda: (lambda s,b: (assert_no_5xx(s,b), f'HTTP {s}')[1])(*requisicao('GET','/pets')))
    teste('API-03', 'Listagem de veterinarios responde sem erro 5xx',
          lambda: (lambda s,b: (assert_no_5xx(s,b), f'HTTP {s}')[1])(*requisicao('GET','/vets')))
    teste('API-04', 'ID de proprietario inexistente retorna 404',
          lambda: (lambda s,b: (exigir_status(s,404,b), f'HTTP {s}')[1])(*requisicao('GET','/owners/999999999')))

    sufixo = uuid.uuid4().hex[:10]
    registro = {'firstName':'QaAutomatizado', 'lastName':'Teste'+sufixo,
                'address':'Rua QA, 100', 'city':'Sao Paulo', 'telephone':'11987654321'}
    owner_id = None

    def cadastrar():
        nonlocal owner_id
        status, body = requisicao('POST','/owners',registro)
        exigir_status(status,201,body)
        dados=json.loads(body)
        assert 'id' in dados and dados['id'] is not None, 'Resposta sem id'
        owner_id=int(dados['id'])
        return f'HTTP 201, ID {owner_id}'

    teste('API-05', 'Cadastrar proprietario com 11 digitos (HTTP 201)', cadastrar)
    if owner_id is not None:
        teste('API-06', 'Consultar proprietario recem-criado (HTTP 200)',
              lambda: (lambda s,b: (exigir_status(s,200,b), f'HTTP {s}')[1])(*requisicao('GET',f'/owners/{owner_id}')))
    else:
        RESULTADOS.append({'id':'API-06','teste':'Consultar proprietario recem-criado',
                           'status':'BLOQUEADO','detalhes':'Dependencia: API-05 nao criou proprietario','duracao_s':0})

    def telefone_invalido():
        invalido = dict(registro)
        invalido['lastName'] = 'Invalido'+sufixo
        invalido['telephone']='12345'
        status, body = requisicao('POST','/owners',invalido)
        assert 400 <= status < 500, f'Esperado erro de validacao 4xx, recebido HTTP {status}: {body[:220]}'
        return f'HTTP {status} (esperado 4xx)'

    teste('API-07', 'Telefone invalido nao deve causar erro 500', telefone_invalido)

    if owner_id is not None:
        def remover():
            status, body = requisicao('DELETE',f'/owners/{owner_id}')
            exigir_status(status,204,body)
            return f'HTTP {status}; cadastro de teste removido'
        teste('API-08', 'Excluir proprietario criado pelo teste (limpeza)', remover)
    else:
        RESULTADOS.append({'id':'API-08','teste':'Excluir proprietario criado pelo teste',
                           'status':'BLOQUEADO','detalhes':'Dependencia: API-05 nao criou proprietario','duracao_s':0})

    salvar()
    falhas=sum(x['status']=='FALHOU' for x in RESULTADOS)
    print(f'RESUMO: {len(RESULTADOS)} casos; {sum(x["status"]=="APROVADO" for x in RESULTADOS)} aprovados; {falhas} falhas; {sum(x["status"]=="BLOQUEADO" for x in RESULTADOS)} bloqueados')
    return 1 if falhas or any(x['status']=='BLOQUEADO' for x in RESULTADOS) else 0


def assert_no_5xx(status, body):
    assert status < 500, f'HTTP {status}: {body[:220]}'


def salvar():
    (OUT/'resultado_api.json').write_text(json.dumps(RESULTADOS,ensure_ascii=False,indent=2),encoding='utf-8')
    with (OUT/'resultado_api.csv').open('w',encoding='utf-8-sig',newline='') as f:
        wr=csv.DictWriter(f,fieldnames=['id','teste','status','detalhes','duracao_s']);wr.writeheader();wr.writerows(RESULTADOS)
    aprovados=sum(x['status']=='APROVADO' for x in RESULTADOS)
    falhas=sum(x['status']=='FALHOU' for x in RESULTADOS)
    bloqueados=sum(x['status']=='BLOQUEADO' for x in RESULTADOS)
    lines=['# Evidencia de testes de API HTTP', '',f'- Base: `{BASE}`',
           f'- Casos: {len(RESULTADOS)}; aprovados: {aprovados}; falhas: {falhas}; bloqueados: {bloqueados}',
           '', '| ID | Teste | Resultado | Observacoes |','|---|---|---|---|']
    for r in RESULTADOS:
        lines.append(f'| {r["id"]} | {r["teste"]} | {r["status"]} | {r["detalhes"].replace("|","/").replace(chr(10)," ")} |')
    (OUT/'RELATORIO_API.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')

if __name__=='__main__':
    sys.exit(main())
