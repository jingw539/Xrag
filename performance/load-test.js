import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const USERNAME = __ENV.USERNAME || '';
const PASSWORD = __ENV.PASSWORD || '';
const CASE_ID = __ENV.CASE_ID || '';
const IMAGE_ID = __ENV.IMAGE_ID || '';
const TOP_K = __ENV.TOP_K || '3';

function login() {
  const payload = JSON.stringify({ username: USERNAME, password: PASSWORD });
  const res = http.post(`${BASE_URL}/api/auth/login`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, {
    'login status 200': (r) => r.status === 200,
    'login has token': (r) => !!r.json('data.accessToken'),
  });
  return res;
}

export default function () {
  if (!USERNAME || !PASSWORD) {
    sleep(1);
    return;
  }

  const loginRes = login();
  const token = loginRes.json('data.accessToken');
  if (!token) {
    sleep(1);
    return;
  }

  const headers = { Authorization: `Bearer ${token}` };

  const meRes = http.get(`${BASE_URL}/api/users/me`, { headers });
  check(meRes, { 'me 200': (r) => r.status === 200 });

  if (CASE_ID && IMAGE_ID) {
    const url = `${BASE_URL}/api/retrieval/search?caseId=${CASE_ID}&imageId=${IMAGE_ID}&topK=${TOP_K}`;
    const ragRes = http.post(url, null, { headers });
    check(ragRes, { 'rag search 200': (r) => r.status === 200 });
  }

  sleep(1);
}
