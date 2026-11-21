#!/usr/bin/env python3
"""Summarize recorded evidence; never infer success from missing artifacts."""
from pathlib import Path
from xml.etree import ElementTree as ET
import json
from datetime import datetime, timezone
root=Path(__file__).resolve().parents[1]
v=root/'verification'
maven=(v/'maven.log').read_text(errors='replace') if (v/'maven.log').exists() else ''
smoke=(v/'smoke.log').read_text(errors='replace') if (v/'smoke.log').exists() else ''
k6_log=(v/'k6.log').read_text(errors='replace') if (v/'k6.log').exists() else ''
reports=list((root/'target/surefire-reports').glob('TEST-*.xml'))
results={'tests':0,'errors':0,'failures':0,'skipped':0}
for f in reports:
    element=ET.parse(f).getroot()
    for k in results:results[k]+=int(element.attrib.get(k,'0'))
coverage=None
jacoco=root/'target/site/jacoco/jacoco.xml'
if jacoco.exists():
    tree=ET.parse(jacoco).getroot()
    for c in tree.findall('counter'):
        if c.attrib.get('type')=='LINE':
            good=int(c.attrib['covered']);bad=int(c.attrib['missed']);coverage=round(100*good/(good+bad),2) if good+bad else 0
stats={}
if (v/'k6.json').exists():
    data=json.loads((v/'k6.json').read_text())
    stats=data.get('metrics',{})
checks={
  'JUnit suite':bool(reports) and results['errors']==0 and results['failures']==0 and 'BUILD SUCCESS' in maven,
  'JaCoCo >= 75%':coverage is not None and coverage>=75,
  'Live smoke': 'PostgreSQL+Redis live smoke completed' in smoke,
  'k6 completed':bool(stats) and 'thresholds on metrics' not in k6_log.lower() and 'level=error' not in k6_log.lower(),
}
# k6 returns nonzero when a configured threshold fails, so the verification scripts stop before report generation.
lines=[f'# Live verification results — {datetime.now(timezone.utc).isoformat()}','',
       '| Check | Observed result |','|---|---|',
       f"| JUnit tests | {results['tests']} tests, {results['failures']} failures, {results['errors']} errors, {results['skipped']} skipped |",
       f"| JaCoCo line coverage | {coverage if coverage is not None else 'NOT MEASURED'}% |",
       f"| Live API smoke | {'PASS' if checks['Live smoke'] else 'NOT VERIFIED'} |",
       f"| k6 outcome | {'PASS (tool thresholds)' if checks['k6 completed'] else 'NOT VERIFIED'} |"]
for k in ['http_reqs','http_req_duration','http_req_failed','dropped_iterations']:
    data=stats.get(k,{})
    if data:lines.append(f'| k6 {k} | {json.dumps(data.get("values",{}),sort_keys=True)} |')
lines+=['','## Raw evidence','',
        '- `verification/maven.log` (Maven build)','- `target/surefire-reports/` (JUnit XML)',
        '- `target/site/jacoco/` (coverage)', '- `verification/smoke.log` (actual PostgreSQL+Redis HTTP requests)',
        '- `verification/k6.log` and `verification/k6.json` (actual k6 run)',
        '', 'Result is only a PASS when the corresponding real tool executed; missing data is NOT VERIFIED.']
(v/'RESULTS.md').write_text('\n'.join(lines)+'\n')
print('\n'.join(lines))
if not all(checks.values()): raise SystemExit('Not all acceptance checks have verifiable pass evidence')
