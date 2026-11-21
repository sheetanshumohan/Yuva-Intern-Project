#!/usr/bin/env python3
"""End-to-end verification against the *running* Docker/PostgreSQL/Redis API.
Requires Python 3.10+ stdlib only. Creates synthetic uniquely named records;
never changes the preloaded suppliers/batches. Leaves audited test records intact.
Exit nonzero on first wrong response. This is NOT an offline or simulated test.
"""
import concurrent.futures
import json
import os
import sys
import uuid
from decimal import Decimal
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = os.environ.get('BASE_URL', 'http://127.0.0.1:8080').rstrip('/')
# Docker Compose reads .env automatically; the smoke runner reads it without shell-eval.
def env_value(key):
    if key in os.environ: return os.environ[key]
    dotenv = os.path.join(os.path.dirname(__file__), '..', '.env')
    if os.path.exists(dotenv):
        for line in open(dotenv, encoding='utf-8'):
            name,sep,value=line.strip().partition('=')
            if sep and name==key: return value.strip().strip('\"').strip("'")
    return None
ADMIN_PASS = env_value('ADMIN_PASSWORD')
STAFF_PASS = env_value('STAFF_PASSWORD')
VIEWER_PASS = env_value('VIEWER_PASSWORD')
if not all([ADMIN_PASS, STAFF_PASS, VIEWER_PASS]):
    sys.exit('ERROR: ADMIN_PASSWORD, STAFF_PASSWORD and VIEWER_PASSWORD must be set in the environment')
RUN = uuid.uuid4().hex[:9]
count = 0

def api(method, route, token=None, body=None, expect=200):
    global count
    headers={'Accept':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    if body is not None: headers['Content-Type']='application/json'
    request=Request(BASE+route, method=method, headers=headers,
                    data=(json.dumps(body).encode() if body is not None else None))
    try:
        with urlopen(request,timeout=15) as response:
            status=response.status;payload=response.read(); response_headers=response.headers
    except HTTPError as ex:
        status=ex.code;payload=ex.read();response_headers=ex.headers
    except URLError as ex:
        raise AssertionError(f'Cannot reach {BASE}: {ex}') from ex
    try: value=json.loads(payload) if payload else None
    except ValueError: value=payload.decode(errors='replace')
    if status!=expect:
        raise AssertionError(f'{method} {route}: expected {expect}, got {status}; body={str(value)[:600]}')
    if route.startswith('/api/v1/') and not response_headers.get('X-Request-ID'):
        raise AssertionError(f'Missing X-Request-ID on {method} {route}')
    count+=1
    return value

def wt(value,unit='KG'): return {'value':value,'unit':unit}
def require(condition,why):
    if not condition: raise AssertionError(why)

api('GET','/actuator/health')
api('GET','/api/v1/batches',expect=401)
api('POST','/api/v1/auth/login',body={'email':'admin@foodplant.local','password':'incorrect'},expect=401)
def login(role,password):
    return api('POST','/api/v1/auth/login',body={'email':role+'@foodplant.local','password':password})['accessToken']
admin=login('admin',ADMIN_PASS)
staff=login('staff',STAFF_PASS)
viewer=login('viewer',VIEWER_PASS)
api('GET','/api/v1/auth/users',admin)
api('POST','/api/v1/suppliers',viewer,{'name':'Permission check','contactEmail':'audit@example.test','originRegion':'Kerala','active':True},403)
api('POST','/api/v1/auth/users',staff,{'email':'forbidden@example.test','password':'Password123456!','role':'ADMIN'},403)
created_user=api('POST','/api/v1/auth/users',admin,{'email':f'smoke-{RUN}@example.test','password':'SmokeOnlyPwd12345!','role':'VIEWER'},201)
require(created_user['role']=='VIEWER','Admin user provisioning returned incorrect role')
# Supplier CRUD and search
supplier=api('POST','/api/v1/suppliers',staff,{'name':'Smoke Spice '+RUN,'contactEmail':'smoke@example.test','originRegion':'Kerala','active':True},201)
sid=supplier['id'];api('GET','/api/v1/suppliers/'+sid,viewer)
api('PUT','/api/v1/suppliers/'+sid,staff,{'name':'Smoke Spice Updated '+RUN,'contactEmail':'smoke@example.test','originRegion':'Kerala','active':True})
require(any(x['id']==sid for x in api('GET','/api/v1/suppliers?search=Smoke%20Spice%20Updated&size=100',viewer)['content']),'Supplier search missing created record')
api('POST','/api/v1/suppliers',staff,{'name':'Smoke Spice Updated '+RUN,'contactEmail':'smoke@example.test','originRegion':'Kerala','active':True},409)
api('GET','/api/v1/suppliers?page=-1',viewer,expect=400)
api('GET','/api/v1/suppliers?size=101',viewer,expect=400)
# Production line CRUD and cache invalidation
before=api('GET','/api/v1/production-lines/summary',viewer)
line=api('POST','/api/v1/production-lines',staff,{'code':'S-'+RUN.upper(),'name':'Smoke Trial '+RUN,'capacityKgPerDay':500,'active':True},201)
lid=line['id'];api('GET','/api/v1/production-lines/'+lid,viewer)
require(any(x['id']==lid for x in api('GET','/api/v1/production-lines/summary',viewer)),'Redis summary was not invalidated on create')
api('PUT','/api/v1/production-lines/'+lid,staff,{'code':'S-'+RUN.upper(),'name':'Smoke Trial Updated '+RUN,'capacityKgPerDay':501,'active':True})
require(any(x['name']=='Smoke Trial Updated '+RUN for x in api('GET','/api/v1/production-lines/summary',viewer)),'Redis summary was not invalidated on update')
api('GET','/api/v1/production-lines?search=Smoke%20Trial',viewer)
api('DELETE','/api/v1/production-lines/'+lid,admin,expect=204)
require(all(x['id']!=lid for x in api('GET','/api/v1/production-lines/summary',viewer)),'Redis summary was not invalidated on delete')
require(len(before)==len(api('GET','/api/v1/production-lines/summary',viewer)),'Production line count did not return to initial value')
# Material CRUD, signed stock movements, and archiving (auditable deletion)
material=api('POST','/api/v1/raw-materials',staff,{'lotCode':'SMK-'+RUN,'supplierId':sid,'materialName':'Smoke Black Pepper','openingStock':wt(5000,'G')},201)
mid=material['id'];require(Decimal(str(material['stockKg']))==Decimal('5'),'Grams not normalized to kg')
api('GET','/api/v1/raw-materials/'+mid,viewer)
api('PUT','/api/v1/raw-materials/'+mid,staff,{'lotCode':'SMK-'+RUN,'supplierId':sid,'materialName':'Smoke Pepper Powder'})
api('PUT','/api/v1/raw-materials/'+mid,staff,{'lotCode':'SMK-'+RUN,'supplierId':sid,'materialName':'Rejected','openingStock':wt(500)},400)
new_balance=api('POST','/api/v1/raw-materials/'+mid+'/adjustments',staff,{'change':wt(2000,'G'),'reason':'Received','increase':True})
require(Decimal(str(new_balance['stockKg']))==Decimal('7'),'Restock not applied')
new_balance=api('POST','/api/v1/raw-materials/'+mid+'/adjustments',staff,{'change':wt(1),'reason':'Used as sample','increase':False})
require(Decimal(str(new_balance['stockKg']))==Decimal('6'),'Stock removal not applied')
api('POST','/api/v1/raw-materials/'+mid+'/adjustments',staff,{'change':wt(1000),'reason':'Overdraw','increase':False},409)
require(len(api('GET','/api/v1/raw-materials/'+mid+'/movements',viewer))==3,'Opening + two adjustments should appear in the ledger')
api('GET','/api/v1/raw-materials?search=Smoke%20Pepper',viewer)
api('POST','/api/v1/raw-materials',staff,{'lotCode':'BAD-'+RUN,'supplierId':sid,'materialName':'Bad subgram','openingStock':wt(0.9,'G')},400)
# Batch CRUD, duplicate identifiers, nullable night-shift QC, traceability
bp={'batchNumber':'SMOKE-001','productName':'Black pepper milling','productionLineId':before[0]['id'],'supplierId':sid,
    'rawWeight':wt(2),'outputWeight':wt(1.85),'producedAt':'2026-11-21T12:00:00Z','shift':'NIGHT',
    'moisturePercent':None,'status':'QUARANTINED','machineCode':'MILL-1'}
first=api('POST','/api/v1/batches',staff,bp,201);bid=first['id']
api('GET','/api/v1/batches/'+bid,viewer)
require(first['moisturePercent'] is None,'Missing moisture converted into zero')
bp2=dict(bp,producedAt='2026-11-21T12:01:00Z')
second=api('POST','/api/v1/batches',staff,bp2,201)
require(len(api('GET','/api/v1/batches/lookup?batchNumber=SMOKE-001',viewer))>=2,'Legacy identifier reuse incorrectly rejected')
require(any(x['id']==bid for x in api('GET','/api/v1/batches?search=SMOKE-001&shift=NIGHT&status=QUARANTINED',viewer)['content']), 'Batch combined filters failed')
api('PUT','/api/v1/batches/'+second['id'],staff,dict(bp2,batchNumber='SMOKE-002'))
api('DELETE','/api/v1/batches/'+second['id'],admin,expect=204)
api('POST','/api/v1/batches',staff,dict(bp,outputWeight=wt(3)),400)
api('GET','/api/v1/batches/not-a-uuid',viewer,expect=400)
api('GET','/api/v1/batches/'+str(uuid.uuid4()),viewer,expect=404)
qc=api('POST','/api/v1/batches/'+bid+'/quality-checks',staff,{'checkedAt':'2026-11-21T14:00:00Z','moisturePercent':None,'result':'PENDING','notes':'Night lab measurement missing'},201)
require(qc['moisturePercent'] is None,'Missing quality moisture not null')
require(len(api('GET','/api/v1/batches/'+bid+'/quality-checks',viewer))==1,'QA not saved')
api('PUT','/api/v1/batches/'+bid,staff,dict(bp,batchNumber='HACKED-ID'),409)
usage=api('POST','/api/v1/batches/'+bid+'/materials',staff,{'materialLotId':mid,'quantity':wt(500,'G')},201)
require(Decimal(str(usage['quantityKg']))==Decimal('0.5'),'Consumed grams not normalized')
api('POST','/api/v1/batches/'+bid+'/materials',staff,{'materialLotId':mid,'quantity':wt(3)},409)
require(len(api('GET','/api/v1/batches/'+bid+'/materials',viewer))==1,'Consumption not recorded')
trace=api('GET','/api/v1/batches/'+bid+'/trace',viewer)
require(trace['supplier']['id']==sid and len(trace['qualityChecks'])==1 and len(trace['materials'])==1,'Trace response incomplete')
api('DELETE','/api/v1/batches/'+bid,admin,expect=409)
api('DELETE','/api/v1/suppliers/'+sid,admin,expect=409)
api('DELETE','/api/v1/raw-materials/'+mid,admin,expect=409)
api('POST','/api/v1/raw-materials/'+mid+'/adjustments',staff,{'change':wt(5.5),'reason':'Audited archive write-off','increase':False})
api('DELETE','/api/v1/raw-materials/'+mid,admin,expect=204)
require(api('GET','/api/v1/raw-materials/'+mid,viewer)['archived'] is True,'Archive flag not persisted')
api('POST','/api/v1/raw-materials/'+mid+'/adjustments',staff,{'change':wt(1),'reason':'Invalid','increase':True},409)
api('POST','/api/v1/batches/'+bid+'/materials',staff,{'materialLotId':mid,'quantity':wt(1)},409)
require(all(x['id']!=mid for x in api('GET','/api/v1/raw-materials?search=Smoke',viewer)['content']), 'Archived lot still visible as active')
# PostgreSQL transaction locking stress test: concurrent consumption must not overspend lot.
stock_lot=api('POST','/api/v1/raw-materials',staff,{'lotCode':'CONC-'+RUN,'supplierId':sid,'materialName':'Concurrent Stress','openingStock':wt(2)},201)
stock_id=stock_lot['id']
stock_batch=api('POST','/api/v1/batches',staff,dict(bp,batchNumber='CONC-'+RUN,rawWeight=wt(2),outputWeight=wt(1.5)),201)
stock_bid=stock_batch['id']
def consume(_):
    try: api('POST','/api/v1/batches/'+stock_bid+'/materials',staff,{'materialLotId':stock_id,'quantity':wt(0.3)},201);return 201
    except AssertionError as ex:
        if 'got 409' in str(ex): return 409
        raise
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
    responses=list(executor.map(consume,range(10)))
require(responses.count(201)==6 and responses.count(409)==4,f'Race/overspend unexpected results: {responses}')
require(Decimal(str(api('GET','/api/v1/raw-materials/'+stock_id,viewer)['stockKg']))==Decimal('0.2'),'Concurrent stock was not serializable')
require(len(api('GET','/api/v1/raw-materials/'+stock_id+'/movements',viewer))==7,'Concurrent stock ledger diverged from stock')
api('GET','/api/v1/unknown-path',viewer,expect=404)
print(f'PASS: {count} HTTP checks (including 10 concurrent transactions); PostgreSQL+Redis live smoke completed')
print(f'Created synthetic test identifiers: run={RUN}; archived lots retained for audit')
