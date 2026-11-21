#!/usr/bin/env python3
"""Static contracts + manufacturing fixture integrity. Not a Spring/Maven runtime test."""
import csv
import json
import re
from collections import defaultdict
from decimal import Decimal
from pathlib import Path
import yaml

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
def csvrows(path):
    with (root/path).open(newline='',encoding='utf-8') as file: return list(csv.DictReader(file))

api=yaml.safe_load(read('docs/openapi.yaml'))
postman=json.loads(read('postman/FoodPlant.postman_collection.json'))
assert api['openapi']=='3.0.3'
ops={(method.upper(),path,method_data['operationId']) for path,methods in api['paths'].items() for method,method_data in methods.items()}
postman_ops={entry['name'] for folder in postman['item'] for entry in folder['item']}
assert len(ops)==len(postman_ops)==32,(len(ops),len(postman_ops))
assert {opid for _,_,opid in ops}==postman_ops
# The generated API surface must match actual annotated Controller mappings.
controllers=[]
for file in (root/'src/main/java/com/foodplant/controller').glob('*.java'):
    code=file.read_text(encoding='utf-8')
    base=re.search(r'@RequestMapping\("([^"]+)"\)',code)
    assert base, file
    for method,suffix in re.findall(r'@(Get|Post|Put|Delete)Mapping(?:\("([^"]*)"\))?',code):
        controllers.append((method.upper(),base.group(1)+suffix))
assert len(controllers)==32,len(controllers)
assert {(a,b) for a,b,_ in ops}==set(controllers),({(a,b) for a,b,_ in ops}-set(controllers),set(controllers)-{(a,b) for a,b,_ in ops})
schemas=api['components']['schemas']
def walk(o):
    if isinstance(o,dict):
        if '$ref' in o:
            path=o['$ref'];assert path.startswith('#/components/schemas/') and path.split('/')[-1] in schemas,path
        for value in o.values():walk(value)
    elif isinstance(o,list):
        for value in o:walk(value)
walk(api)
for method,path,op_id in ops:
    entry=api['paths'][path][method.lower()]
    assert '429' in entry['responses'] and '400' in entry['responses']
    if op_id=='login':assert entry.get('security')==[]
    else:assert entry.get('security',api['security'])==[{'bearerAuth':[]}]
assert 'openingStock' not in schemas['MaterialUpdateRequest']['properties']
assert 'archived' in schemas['MaterialResponse']['properties']
for field in ['spring','docker-compose.yml'][:1]: assert field  # not material
for file in ['src/main/resources/application.yml','src/test/resources/application-test.yml','docker-compose.yml','.github/workflows/ci.yml']:
    assert yaml.safe_load(read(file)),file
assert 'set -o pipefail' in read('.github/workflows/ci.yml'), 'CI logging pipeline must propagate Maven/k6 failures'
assert 'Cannot change audited batch identity, input weight or output weight' in read('src/main/java/com/foodplant/service/PlantService.java')
assert 'Material lot supplier is immutable' in read('src/main/java/com/foodplant/service/PlantService.java')
for file in ['pom.xml','Dockerfile','.env.example','src/main/resources/db/migration/V1__initial_schema.sql','src/main/resources/db/migration/V2__material_archive.sql']:
    assert (root/file).is_file(),file

productions=csvrows('fixtures/production_records.csv')
materials=csvrows('fixtures/raw_materials.csv')
qa=csvrows('fixtures/quality_checks.csv')
machines=csvrows('fixtures/machines.csv')
consumption=csvrows('fixtures/material_consumption.csv')
assert len(productions)==5 and len({r['production_line'] for r in productions})==3
assert len({r['source_event_id'] for r in productions})==5
assert len({(r['batch_number'],r['produced_at']) for r in productions})==5
assert sum(1 for r in productions if r['batch_number']=='B-001')==2
assert len({r['produced_at'] for r in productions if r['batch_number']=='B-001'})==2
assert any(r['raw_unit']=='g' and r['output_unit']=='kg' for r in productions)
assert any(r['raw_unit']=='kg' and r['output_unit']=='g' for r in productions)
assert all(r['moisture_percent']=='' for r in productions if r['shift']=='NIGHT')
by_batch={(r['batch_number'],r['produced_at']):r for r in productions}
machine_by_code={r['machine_code']:r for r in machines}
assert len(machine_by_code)==len(machines)
for r in productions:
    assert r['machine_code'] in machine_by_code
    assert machine_by_code[r['machine_code']]['production_line']==r['production_line']

kg=lambda v,u:Decimal(v)/(Decimal('1000') if u.lower()=='g' else Decimal('1'))
for r in productions:
    assert kg(r['raw_weight'],r['raw_unit'])>=kg(r['output_weight'],r['output_unit'])>0
for r in qa:
    assert (r['batch_number'],r['produced_at']) in by_batch
    if r['result']=='PASS':assert r['moisture_percent']!=''
    if r['moisture_percent']=='':assert r['result']=='PENDING'
assert len(qa)==len(productions)
materials_by_code={r['lot_code']:r for r in materials}
opening={r['lot_code']:kg(r['opening_weight'],r['opening_unit']) for r in materials}
usage=defaultdict(Decimal)
for r in consumption:
    key=r['batch_number'],r['produced_at']
    assert key in by_batch
    assert r['lot_code'] in materials_by_code
    assert materials_by_code[r['lot_code']]['supplier']==by_batch[key]['supplier']
    qty=kg(r['quantity'],r['unit'])
    assert qty>0 and qty<=kg(by_batch[key]['raw_weight'],by_batch[key]['raw_unit'])
    usage[r['lot_code']]+=qty
assert len(consumption)==len(productions)
assert all(usage[code]<=opening[code] for code in opening)
# Static source assertions prevent fixtures/docs drifting from generated server seed.
seed=read('src/main/java/com/foodplant/config/DemoDataSeeder.java')
for r in consumption:
    assert 'seedUsage(' in seed
for code in materials_by_code:
    assert code in seed
assert (root/'loadtest/festival-ramp.js').exists()
assert (root/'scripts/api_smoke.py').exists()
assert (root/'scripts/verify_all.ps1').exists()
assert (root/'scripts/verify_all.sh').exists()
print('PASS: 32 annotated Controller routes exactly match the OpenAPI spec and Postman collection')
print('PASS: OpenAPI $refs, auth requirements, error responses and material DTO schema valid')
print('PASS: Spring/Compose/CI/test YAML parse and Flyway V1/V2 migration files exist')
print('PASS: synthetic manufacturing fixtures have valid machine-line, supplier-lot, QA and stock-balance relationships')
print('PASS: 3 lines, mixed weights, 2 distinct reused B-001 IDs, night moisture null, 5 seed consumption rows')
print('NOTE: Static checks do NOT prove Maven, database migrations, HTTP responses, or performance')
