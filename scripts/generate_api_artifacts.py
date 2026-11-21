#!/usr/bin/env python3
"""Deterministic source-controlled OpenAPI and Postman collection generator."""
import json
from pathlib import Path
import yaml
ROOT = Path(__file__).resolve().parents[1]

schemas = {
    'WeightInput': {'type':'object','required':['value','unit'],'properties':{'value':{'type':'number','exclusiveMinimum':0},'unit':{'type':'string','enum':['KG','G']}}},
    'LoginRequest': {'type':'object','required':['email','password'],'properties':{'email':{'type':'string','format':'email'},'password':{'type':'string'}}},
    'TokenResponse': {'type':'object','properties':{'accessToken':{'type':'string'},'tokenType':{'type':'string'},'expiresInSeconds':{'type':'integer'},'role':{'type':'string'}}},
    'UserRequest': {'type':'object','required':['email','password','role'],'properties':{'email':{'type':'string','format':'email'},'password':{'type':'string','minLength':12},'role':{'type':'string','enum':['ADMIN','STAFF','VIEWER']}}},
    'UserResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'email':{'type':'string'},'role':{'type':'string'},'enabled':{'type':'boolean'}}},
    'SupplierRequest': {'type':'object','required':['name','contactEmail','originRegion','active'],'properties':{'name':{'type':'string'},'contactEmail':{'type':'string','format':'email'},'originRegion':{'type':'string'},'active':{'type':'boolean'}}},
    'SupplierResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'name':{'type':'string'},'contactEmail':{'type':'string'},'originRegion':{'type':'string'},'active':{'type':'boolean'}}},
    'ProductionLineRequest': {'type':'object','required':['code','name','capacityKgPerDay','active'],'properties':{'code':{'type':'string','pattern':'^[A-Z0-9-]{2,20}$'},'name':{'type':'string'},'capacityKgPerDay':{'type':'number','minimum':0.001},'active':{'type':'boolean'}}},
    'ProductionLineResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'code':{'type':'string'},'name':{'type':'string'},'capacityKgPerDay':{'type':'number'},'active':{'type':'boolean'}}},
    'MaterialRequest': {'type':'object','required':['lotCode','supplierId','materialName','openingStock'],'properties':{'lotCode':{'type':'string'},'supplierId':{'type':'string','format':'uuid'},'materialName':{'type':'string'},'openingStock':{'$ref':'#/components/schemas/WeightInput'},'expiryDate':{'type':'string','format':'date','nullable':True}}},
    'MaterialUpdateRequest': {'type':'object','required':['lotCode','supplierId','materialName'],'additionalProperties':False,'properties':{'lotCode':{'type':'string','maxLength':100},'supplierId':{'type':'string','format':'uuid'},'materialName':{'type':'string','maxLength':140},'expiryDate':{'type':'string','format':'date','nullable':True}}},
    'MaterialResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'lotCode':{'type':'string'},'supplierId':{'type':'string','format':'uuid'},'materialName':{'type':'string'},'stockKg':{'type':'number'},'expiryDate':{'type':'string','format':'date','nullable':True},'archived':{'type':'boolean'}}},
    'StockAdjustmentRequest': {'type':'object','required':['change','reason','increase'],'properties':{'change':{'$ref':'#/components/schemas/WeightInput'},'reason':{'type':'string'},'increase':{'type':'boolean'}}},
    'MovementResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'materialLotId':{'type':'string','format':'uuid'},'changeKg':{'type':'number'},'reason':{'type':'string'},'createdAt':{'type':'string','format':'date-time'}}},
    'BatchRequest': {'type':'object','required':['batchNumber','productName','productionLineId','supplierId','rawWeight','outputWeight','producedAt','shift','status','machineCode'],'properties':{
       'batchNumber':{'type':'string','description':'Legacy number; NOT unique after reset'},'productName':{'type':'string'},'productionLineId':{'type':'string','format':'uuid'},'supplierId':{'type':'string','format':'uuid'},'rawWeight':{'$ref':'#/components/schemas/WeightInput'},'outputWeight':{'$ref':'#/components/schemas/WeightInput'},'producedAt':{'type':'string','format':'date-time'},'shift':{'type':'string','enum':['DAY','EVENING','NIGHT']},'moisturePercent':{'type':'number','minimum':0,'maximum':100,'nullable':True},'status':{'type':'string','enum':['PLANNED','IN_PROGRESS','COMPLETED','QUARANTINED']},'machineCode':{'type':'string'},'notes':{'type':'string','nullable':True}}},
    'BatchResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'batchNumber':{'type':'string'},'productName':{'type':'string'},'productionLineId':{'type':'string','format':'uuid'},'supplierId':{'type':'string','format':'uuid'},'rawWeightKg':{'type':'number'},'outputWeightKg':{'type':'number'},'producedAt':{'type':'string','format':'date-time'},'shift':{'type':'string'},'moisturePercent':{'type':'number','nullable':True},'status':{'type':'string'},'machineCode':{'type':'string'},'notes':{'type':'string','nullable':True}}},
    'QualityRequest': {'type':'object','required':['checkedAt','result'],'properties':{'checkedAt':{'type':'string','format':'date-time'},'moisturePercent':{'type':'number','nullable':True,'minimum':0,'maximum':100},'result':{'type':'string','enum':['PASS','FAIL','PENDING']},'notes':{'type':'string','nullable':True}}},
    'QualityResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'batchId':{'type':'string','format':'uuid'},'checkedAt':{'type':'string','format':'date-time'},'moisturePercent':{'type':'number','nullable':True},'result':{'type':'string'},'notes':{'type':'string','nullable':True}}},
    'UsageRequest': {'type':'object','required':['materialLotId','quantity'],'properties':{'materialLotId':{'type':'string','format':'uuid'},'quantity':{'$ref':'#/components/schemas/WeightInput'}}},
    'UsageResponse': {'type':'object','properties':{'id':{'type':'string','format':'uuid'},'batchId':{'type':'string','format':'uuid'},'materialLotId':{'type':'string','format':'uuid'},'quantityKg':{'type':'number'}}},
    'TraceResponse': {'type':'object','properties':{'batch':{'$ref':'#/components/schemas/BatchResponse'},'supplier':{'$ref':'#/components/schemas/SupplierResponse'},'productionLine':{'$ref':'#/components/schemas/ProductionLineResponse'},'qualityChecks':{'type':'array','items':{'$ref':'#/components/schemas/QualityResponse'}},'materials':{'type':'array','items':{'$ref':'#/components/schemas/UsageResponse'}}}},
    'Problem':{'type':'object','properties':{'timestamp':{'type':'string','format':'date-time'},'status':{'type':'integer'},'error':{'type':'string'},'message':{'type':'string'},'path':{'type':'string'},'fields':{'type':'object','additionalProperties':{'type':'string'}}}}
}

# method, path, operationId, tag, summary, body schema, response schema, required role
ops = [
('POST','/api/v1/auth/login','login','Authentication','Exchange credentials for signed JWT','LoginRequest','TokenResponse','PUBLIC'),
('POST','/api/v1/auth/users','createUser','Authentication','Provision a new user (admin)','UserRequest','UserResponse','ADMIN'),
('GET','/api/v1/auth/users','listUsers','Authentication','List user accounts (admin)',None,'UserResponse[]','ADMIN'),
('GET','/api/v1/suppliers','listSuppliers','Suppliers','Search suppliers with pagination',None,'SupplierResponse.page','VIEWER'),
('POST','/api/v1/suppliers','createSupplier','Suppliers','Add supplier','SupplierRequest','SupplierResponse','STAFF'),
('GET','/api/v1/suppliers/{id}','getSupplier','Suppliers','Get supplier by UUID',None,'SupplierResponse','VIEWER'),
('PUT','/api/v1/suppliers/{id}','updateSupplier','Suppliers','Replace supplier','SupplierRequest','SupplierResponse','STAFF'),
('DELETE','/api/v1/suppliers/{id}','deleteSupplier','Suppliers','Delete unreferenced supplier',None,None,'ADMIN'),
('GET','/api/v1/production-lines','listLines','Production Lines','Search lines with pagination',None,'ProductionLineResponse.page','VIEWER'),
('POST','/api/v1/production-lines','createLine','Production Lines','Add line','ProductionLineRequest','ProductionLineResponse','STAFF'),
('GET','/api/v1/production-lines/summary','lineSummary','Production Lines','Redis-cached all-lines summary; k6 load-test target',None,'ProductionLineResponse[]','VIEWER'),
('GET','/api/v1/production-lines/{id}','getLine','Production Lines','Get line',None,'ProductionLineResponse','VIEWER'),
('PUT','/api/v1/production-lines/{id}','updateLine','Production Lines','Update line and invalidate summary cache','ProductionLineRequest','ProductionLineResponse','STAFF'),
('DELETE','/api/v1/production-lines/{id}','deleteLine','Production Lines','Delete unreferenced line',None,None,'ADMIN'),
('GET','/api/v1/raw-materials','listMaterials','Raw Materials','Search raw material lots with pagination',None,'MaterialResponse.page','VIEWER'),
('POST','/api/v1/raw-materials','createMaterial','Raw Materials','Open new stock lot; track opening movement','MaterialRequest','MaterialResponse','STAFF'),
('GET','/api/v1/raw-materials/{id}','getMaterial','Raw Materials','Get material lot',None,'MaterialResponse','VIEWER'),
('PUT','/api/v1/raw-materials/{id}','updateMaterial','Raw Materials','Update metadata; stock must be adjusted separately','MaterialUpdateRequest','MaterialResponse','STAFF'),
('DELETE','/api/v1/raw-materials/{id}','deleteMaterial','Raw Materials','Archive zero-stock material without deleting its stock audit history',None,None,'ADMIN'),
('GET','/api/v1/raw-materials/{id}/movements','lotMovements','Raw Materials','List all inventory ledger movements for lot',None,'MovementResponse[]','VIEWER'),
('POST','/api/v1/raw-materials/{id}/adjustments','adjustStock','Raw Materials','Atomic increase or decrease with audited movement','StockAdjustmentRequest','MaterialResponse','STAFF'),
('GET','/api/v1/batches','listBatches','Batches','Filter by search, lineId, status and shift with pagination',None,'BatchResponse.page','VIEWER'),
('POST','/api/v1/batches','createBatch','Batches','Create traceable batch; kg and grams accepted','BatchRequest','BatchResponse','STAFF'),
('GET','/api/v1/batches/lookup','lookupBatches','Batches','Find all UUID records sharing legacy batchNumber',None,'BatchResponse[]','VIEWER'),
('GET','/api/v1/batches/{id}','getBatch','Batches','Get batch by unique UUID',None,'BatchResponse','VIEWER'),
('PUT','/api/v1/batches/{id}','updateBatch','Batches','Update batch; protect consumed records','BatchRequest','BatchResponse','STAFF'),
('DELETE','/api/v1/batches/{id}','deleteBatch','Batches','Reject removal of batches with QA or usage history',None,None,'ADMIN'),
('GET','/api/v1/batches/{id}/trace','traceBatch','Traceability','Join batch, supplier, line, QA and consumption',None,'TraceResponse','VIEWER'),
('GET','/api/v1/batches/{id}/quality-checks','listQualityChecks','Quality','List QA observations for batch',None,'QualityResponse[]','VIEWER'),
('POST','/api/v1/batches/{id}/quality-checks','addQualityCheck','Quality','Add QA observation; missing moisture is allowed','QualityRequest','QualityResponse','STAFF'),
('GET','/api/v1/batches/{id}/materials','listBatchMaterials','Traceability','List materials consumed by batch',None,'UsageResponse[]','VIEWER'),
('POST','/api/v1/batches/{id}/materials','consumeMaterial','Traceability','Deduct batch material atomically and append ledger','UsageRequest','UsageResponse','STAFF'),
]

spec = {
 'openapi':'3.0.3',
 'info':{'title':'Food Plant Traceability & Inventory REST API','version':'1.0.0',
    'description':'Fictional Kochi spice processing unit. All weights normalized to kg; batchNumber is reusable, UUID is stable identifier.'},
 'servers':[{'url':'http://localhost:8080','description':'Local Docker Compose'}],
 'tags':[{'name':t} for t in ['Authentication','Suppliers','Production Lines','Raw Materials','Batches','Quality','Traceability']],
 'security':[{'bearerAuth':[]}],
 'paths':{},
 'components':{'securitySchemes':{'bearerAuth':{'type':'http','scheme':'bearer','bearerFormat':'JWT'}},'schemas':schemas}
}

queries={
 'listSuppliers':[('search','string'),('page','integer'),('size','integer')],
 'listLines':[('search','string'),('page','integer'),('size','integer')],
 'listMaterials':[('search','string'),('page','integer'),('size','integer')],
 'listBatches':[('search','string'),('lineId','string'),('status','string'),('shift','string'),('page','integer'),('size','integer')],
 'lookupBatches':[('batchNumber','string')],
}
example = {
 'LoginRequest':{'email':'admin@foodplant.local','password':'YOUR_ADMIN_PASSWORD'},
 'SupplierRequest':{'name':'Example Supply Co-op','contactEmail':'hello@example.org','originRegion':'Wayanad, Kerala','active':True},
 'ProductionLineRequest':{'code':'LINE-04','name':'Trial Packing Line','capacityKgPerDay':600,'active':True},
 'MaterialUpdateRequest':{'lotCode':'LOT-NEW-01','supplierId':'00000000-0000-0000-0000-000000000001','materialName':'Pepper'},
 'MaterialRequest':{'lotCode':'LOT-NEW-01','supplierId':'00000000-0000-0000-0000-000000000001','materialName':'Pepper','openingStock':{'value':2500,'unit':'G'}},
 'BatchRequest':{'batchNumber':'B-NEW-001','productName':'Ground Pepper','productionLineId':'00000000-0000-0000-0000-000000000002','supplierId':'00000000-0000-0000-0000-000000000001','rawWeight':{'value':3500,'unit':'G'},'outputWeight':{'value':3.25,'unit':'KG'},'producedAt':'2026-11-21T08:30:00Z','shift':'NIGHT','moisturePercent':None,'status':'COMPLETED','machineCode':'MILL-1'},
 'QualityRequest':{'checkedAt':'2026-11-21T23:00:00Z','moisturePercent':None,'result':'PENDING','notes':'Lab closed overnight'},
 'UsageRequest':{'materialLotId':'00000000-0000-0000-0000-000000000003','quantity':{'value':500,'unit':'G'}},
 'StockAdjustmentRequest':{'change':{'value':50,'unit':'KG'},'increase':True,'reason':'Received shipment'},
 'UserRequest':{'email':'newstaff@foodplant.local','password':'STRONG_RANDOM_PASSWORD','role':'STAFF'},
}

for method,path,ident,tag,summary,body_name,return_type,role in ops:
 operation={'operationId':ident,'tags':[tag],'summary':summary,
            'description':'Required role: '+role+' (ADMIN also has write privileges).' if role!='PUBLIC' else 'No JWT required.',
            'responses':{}}
 if role=='PUBLIC':operation['security']=[]
 if body_name:
  operation['requestBody']={'required':True,'content':{'application/json':{'schema':{'$ref':f'#/components/schemas/{body_name}'}}}}
 path_params=[]
 for seg in path.split('/'):
  if seg.startswith('{'):
   path_params.append({'in':'path','name':seg[1:-1],'required':True,'schema':{'type':'string','format':'uuid'}})
 for name,typ in queries.get(ident,[]):
  path_params.append({'in':'query','name':name,'required':ident=='lookupBatches',
         'schema':{'type':typ,'minimum':0 if name=='page' else 1} if name in ('page','size') else {'type':typ}})
 if path_params:operation['parameters']=path_params
 success='204' if method=='DELETE' else '201' if method=='POST' and ident!='login' else '200'
 response={'description':'Successful operation'}
 if return_type:
  kind=return_type.split('.')[0].removesuffix('[]')
  if return_type.endswith('[]'):shape={'type':'array','items':{'$ref':f'#/components/schemas/{kind}'}}
  elif return_type.endswith('.page'):shape={'type':'object','properties':{'content':{'type':'array','items':{'$ref':f'#/components/schemas/{kind}'}},'totalElements':{'type':'integer'},'totalPages':{'type':'integer'},'number':{'type':'integer'},'size':{'type':'integer'}}}
  else:shape={'$ref':f'#/components/schemas/{kind}'}
  response['content']={'application/json':{'schema':shape}}
 operation['responses'][success]=response
 for code,label in [('400','Validation failed'),('401','JWT missing or invalid'),('403','Insufficient privileges'),('404','Resource not found'),('409','Conflict / business rule rejected'),('429','Rate limited')]:
  if role=='PUBLIC' and code in ('403','404','409'):continue
  operation['responses'][code]={'description':label,'content':{'application/json':{'schema':{'$ref':'#/components/schemas/Problem'}}}}
 operation['responses']['500']={'description':'Sanitized unexpected error (no stack traces)'}
 spec['paths'].setdefault(path,{})[method.lower()]=operation

(ROOT/'docs/openapi.yaml').write_text(yaml.safe_dump(spec,sort_keys=False,allow_unicode=True),encoding='utf-8')

folders={}
for method,path,ident,tag,summary,body_name,return_type,role in ops:
 pm_path=path.replace('{id}','{{id}}')
 url='{{baseUrl}}'+pm_path
 if ident=='lookupBatches':url+='?batchNumber=B-001'
 if ident in ('listSuppliers','listLines','listMaterials','listBatches'):url+='?page=0&size=20'
 request={'method':method,'header':[], 'url':url,'description':summary+'\nRequired role: '+role}
 if ident!='login':request['auth']={'type':'bearer','bearer':[{'key':'token','value':'{{token}}','type':'string'}]}
 if body_name:
  request['header'].append({'key':'Content-Type','value':'application/json'})
  request['body']={'mode':'raw','raw':json.dumps(example.get(body_name,{}),indent=2),'options':{'raw':{'language':'json'}}}
 item={'name':ident,'request':request,'response':[]}
 if ident=='login':
  item['event']=[{'listen':'test','script':{'type':'text/javascript','exec':['if (pm.response.code === 200) {','  pm.collectionVariables.set("token", pm.response.json().accessToken);','}']}}]
 if ident in ('createSupplier','createLine','createMaterial','createBatch'):
  item['event']=[{'listen':'test','script':{'type':'text/javascript','exec':['if ([200,201].includes(pm.response.code)) {','  pm.collectionVariables.set("id", pm.response.json().id);','}']}}]
 folders.setdefault(tag,[]).append(item)
collection={
 'info':{'name':'Food Plant API | 32 endpoints','_postman_id':'382a4cd5-b6e3-49a1-8f1c-979e4a781001',
         'description':'Run Authentication > login first. Replace example UUIDs with IDs from list endpoints.',
         'schema':'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
 'item':[{'name':tag,'item':items} for tag,items in folders.items()],
 'variable':[{'key':'baseUrl','value':'http://localhost:8080'}, {'key':'token','value':''}, {'key':'id','value':''}]
}
(ROOT/'postman/FoodPlant.postman_collection.json').write_text(json.dumps(collection,indent=2),encoding='utf-8')
print(f'Wrote {len(ops)} documented operations and Postman requests')
