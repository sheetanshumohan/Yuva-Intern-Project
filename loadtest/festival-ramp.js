import http from 'k6/http';
import { check, sleep } from 'k6';

// Example: k6 run -e BASE_URL=http://localhost:8080 -e LOGIN_EMAIL=viewer@foodplant.local \
//                -e LOGIN_PASSWORD='your-password' loadtest/festival-ramp.js
const base = __ENV.BASE_URL || 'http://localhost:8080';
export const options = {
  scenarios: {
    festival_ramp: {
      executor: 'constant-arrival-rate', rate: 200, timeUnit: '1s', duration: '60s',
      preAllocatedVUs: 120, maxVUs: 500,
    },
  },
  thresholds: {
    'http_req_duration{endpoint:line_summary}': ['p(95)<300'],
    'http_req_failed{endpoint:line_summary}': ['rate<0.01'],
    'dropped_iterations': ['count==0'],
    'checks': ['rate>0.99'],
  },
};
export function setup() {
  const login=http.post(`${base}/api/v1/auth/login`,JSON.stringify({
    email: __ENV.LOGIN_EMAIL || 'viewer@foodplant.local',
    password: __ENV.LOGIN_PASSWORD || ''
  }),{headers:{'Content-Type':'application/json'}});
  if(login.status !== 200) throw new Error(`Login failed: HTTP ${login.status}`);
  return {token: login.json('accessToken')};
}
export default function(data) {
  const res=http.get(`${base}/api/v1/production-lines/summary`,{
    headers:{Authorization:`Bearer ${data.token}`},tags:{endpoint:'line_summary'}
  });
  check(res,{'summary returns HTTP 200': r=>r.status===200,
             'summary includes three lines': r=>{try{return r.json().length>=3;}catch(e){return false;}}});
}
