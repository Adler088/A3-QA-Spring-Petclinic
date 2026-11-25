from pathlib import Path
import xml.etree.ElementTree as ET

arq=Path('target/site/jacoco/jacoco.xml')
if not arq.exists():
    raise SystemExit('Relatorio nao encontrado. Execute o Maven com jacoco:report primeiro.')
root=ET.parse(arq).getroot()
contadores={e.attrib['type']:e.attrib for e in root.findall('counter')}
linhas=['# Cobertura JaCoCo — Spring Petclinic', '',
        '> Percentuais calculados a partir de `target/site/jacoco/jacoco.xml` deste computador.', '',
        '| Metrica | Cobertos | Nao cobertos | Cobertura |','|---|---:|---:|---:|']
for chave,nome in [('LINE','Linhas'),('BRANCH','Desvios'),('INSTRUCTION','Instrucoes'),('METHOD','Metodos'),('CLASS','Classes')]:
    c=contadores.get(chave)
    if c:
        sim=int(c['covered']);nao=int(c['missed']);total=sim+nao
        p=100*sim/total if total else 0
        linhas.append(f'| {nome} | {sim} | {nao} | {p:.2f}% |')
saida=Path('relatorio_cobertura.md');saida.write_text('\n'.join(linhas)+'\n',encoding='utf-8')
print(saida.read_text(encoding='utf-8'))
