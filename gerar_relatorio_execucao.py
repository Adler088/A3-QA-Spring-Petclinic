from pathlib import Path
import json
import xml.etree.ElementTree as ET

base = Path('target/surefire-reports')
relatorio = Path('DOCUMENTACAO_A3/RESULTADOS_EXECUCAO_ATUAL.md')
relatorio.parent.mkdir(parents=True, exist_ok=True)
linhas = ['# Resultado atual de execução — A3 Spring Petclinic', '']
xmls = list(base.glob('TEST-*.xml')) if base.exists() else []
selecionados = ('OwnerValidationTest', 'ModelRelationsQaTest', 'RootRestControllerV1QaTest', 'GitHubActionsQaTest', 'OwnerControllerExtraQaTest')
xmls = [x for x in xmls if any(nome in x.name for nome in selecionados)]
if xmls:
    total = falhas = erros = ignorados = 0
    for arquivo in xmls:
        raiz = ET.parse(arquivo).getroot()
        total += int(raiz.get('tests', '0'))
        falhas += int(raiz.get('failures', '0'))
        erros += int(raiz.get('errors', '0'))
        ignorados += int(raiz.get('skipped', '0'))
    linhas += ['## JUnit', '', f'- Casos: {total}', f'- Aprovados: {total - falhas - erros - ignorados}', f'- Falhas: {falhas}', f'- Erros: {erros}', f'- Ignorados: {ignorados}', '']
else:
    linhas += ['## JUnit', '', 'Nenhum XML dos testes QA foi encontrado nesta execução.', '']
api = Path('evidencias_api/resultado_api.json')
if api.exists():
    resultados = json.loads(api.read_text(encoding='utf-8'))
    linhas += ['## Testes HTTP', '', f'- Casos: {len(resultados)}']
    for situacao in ('APROVADO', 'FALHOU', 'BLOQUEADO'):
        linhas.append(f'- {situacao.capitalize()}: {sum(x.get("status") == situacao for x in resultados)}')
    linhas.append('')
else:
    linhas += ['## Testes HTTP', '', 'Nenhum resultado atual encontrado. O backend precisa estar acessível em localhost:9966.', '']
cobertura = Path('relatorio_cobertura.md')
if cobertura.exists():
    linhas += ['## JaCoCo', '', cobertura.read_text(encoding='utf-8').strip(), '']
else:
    linhas += ['## JaCoCo', '', 'Relatório de cobertura desta execução não encontrado.', '']
linhas += ['## Observações', '', 'Os indicadores acima foram extraídos dos relatórios existentes na pasta de execução. Confirme que os arquivos são de uma execução recente antes de utilizar os valores como evidência.', '']
relatorio.write_text('\n'.join(linhas), encoding='utf-8')
print(relatorio.read_text(encoding='utf-8'))
