#!/usr/bin/env python3
"""Wait for /actuator/health to turn UP (does not replace an acceptance test)."""
import json
import os
import sys
import time
from urllib.request import urlopen
url=os.environ.get('BASE_URL','http://127.0.0.1:8080').rstrip('/')+'/actuator/health'
for _ in range(90):
    try:
        with urlopen(url,timeout=2) as response:
            data=json.load(response)
        if data.get('status')=='UP':
            print('PASS: API health is UP');break
    except Exception: pass
    time.sleep(2)
else:
    sys.exit(f'API never reported UP: {url}. Check docker compose logs api')
