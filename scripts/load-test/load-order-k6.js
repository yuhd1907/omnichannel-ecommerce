// Load test THONG LUONG dat hang qua API Gateway: tang dan 100 -> 500 -> 1000 request/giay.
// (scripts/load-test/flashsale-k6.js do TINH DUNG voi 100 nguoi / kho 10; script nay do SUC CHIU.)
//
// Moi request: POST /api/v1/orders mua 1 LOAD-001 (seed boi product-service V3, kho dat bang
// load-reset.sql). Het kho thi request nhan 409 OUT_OF_STOCK qua duong tu choi som (khong goi
// identity/product/Redis) -> phan lon tai sau khi het hang la duong nay.
//
// ── Chay (PowerShell, thu muc goc repo, stack dang chay bang docker compose) ──────────────
// 1. Nang rate limit dang nhap/dang ky cho setup (tao 300 user tu 1 IP). Them vao .env roi
//    'docker compose up -d api-gateway':
//      RATE_LIMIT_LOGIN_REPLENISH=200
//      RATE_LIMIT_LOGIN_BURST=400
//      RATE_LIMIT_REPLENISH=200
//      RATE_LIMIT_BURST=400
//    (Khong nang cung chay duoc, chi la setup lau hon vi tu cho khi gap 429.)
//    Nen tat gui mail khi load test: MAIL_ENABLED=false roi 'docker compose up -d notification-service'.
// 2. Reset kho:
//      Get-Content scripts\sql\load-reset.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db
// 3. Chay k6:
//      Get-Content scripts\load-test\load-order-k6.js | docker run --rm -i --network host grafana/k6 run -
//    Docker Desktop Windows neu --network host khong toi duoc localhost:
//      Get-Content scripts\load-test\load-order-k6.js | docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run -
//    Doi muc tai: -e PEAK_RPS=500 ; doi so user: -e USERS=500
// 4. Kiem tra kho (4 cot cuoi phai la t):
//      Get-Content scripts\sql\load-verify.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db
// 5. Xem Grafana http://localhost:3000 (dashboard "Omnichannel - Tong quan") trong luc chay.
// 6. Xong thi xoa 4 dong RATE_LIMIT_* khoi .env va 'docker compose up -d api-gateway'.
//
// Doc ket qua: 'dropped_iterations' > 0 nghia la he thong khong theo kip muc tai yeu cau
// (k6 het VU de gui) -> muc req/s thuc te dat duoc la 'http_reqs' / thoi gian.
// other_status > 0 thuong la request timeout (status 0) o giai doan con hang. Ket qua lan chay
// 13/10/2026 va cach doc: docs/Flashsale_Results.md, muc "Load test thong luong".

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API      = `${BASE_URL}/api/v1`;
const SKU      = __ENV.SKU || 'LOAD-001';
const USERS    = Number(__ENV.USERS || 300);
const PEAK_RPS = Number(__ENV.PEAK_RPS || 1000);

const created201    = new Counter('created_201');
const outOfStock409 = new Counter('out_of_stock_409');
const busy409       = new Counter('inventory_busy_409');
const rateLimit429  = new Counter('rate_limited_429');
const otherStatus   = new Counter('other_status');

// 201 va 409 la ket qua nghiep vu hop le -> khong tinh vao http_req_failed.
http.setResponseCallback(http.expectedStatuses(201, 409));

export const options = {
  setupTimeout: '600s',
  scenarios: {
    ramp_orders: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1500,
      stages: [
        { target: 100,                          duration: '20s' },
        { target: Math.round(PEAK_RPS / 2),     duration: '30s' },
        { target: PEAK_RPS,                     duration: '30s' },
        { target: PEAK_RPS,                     duration: '30s' },  // giu dinh
        { target: 0,                            duration: '10s' },
      ],
    },
  },
  thresholds: {
    // Muc tieu: khong co loi he thong (5xx, timeout); 429 cung tinh la loi o day.
    http_req_failed: ['rate<0.01'],
    other_status: ['count==0'],
    'http_req_duration{name:create_order}': ['p(95)<2000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Gui batch, request nao bi 429 (rate limit) thi cho roi gui lai rieng request do.
function batchWithRetry(requests, okStatus, label) {
  const results = new Array(requests.length);
  let pending = requests.map((_, i) => i);
  for (let attempt = 0; attempt < 30 && pending.length > 0; attempt++) {
    // Callback rieng cho setup: login tra 200 khong duoc tinh vao http_req_failed
    // (callback chung chi coi 201/409 la hop le cho buoc dat hang).
    const callback = http.expectedStatuses(...okStatus, 429);
    const responses = http.batch(pending.map((i) => {
      const [method, url, body, params] = requests[i];
      return [method, url, body, { ...params, responseCallback: callback }];
    }));
    const retry = [];
    responses.forEach((r, k) => {
      const i = pending[k];
      if (r.status === 429) retry.push(i);
      else if (!okStatus.includes(r.status)) throw new Error(`${label} failed ${r.status}: ${String(r.body).slice(0, 200)}`);
      else results[i] = r;
    });
    pending = retry;
    if (pending.length > 0) sleep(1);
  }
  if (pending.length > 0) throw new Error(`${label}: van bi 429 sau 30 lan thu, hay nang RATE_LIMIT_*`);
  return results;
}

export function setup() {
  const run = Date.now();
  const users = [];
  const BATCH = 25;

  for (let start = 0; start < USERS; start += BATCH) {
    const idx = [];
    for (let i = start; i < Math.min(start + BATCH, USERS); i++) idx.push(i);
    const emails = idx.map((i) => `load-${run}-${i}@test.com`);

    batchWithRetry(emails.map((email) => ['POST', `${API}/auth/register`,
      JSON.stringify({ email, password: '123456', fullName: 'Load Buyer' }), { headers: JSON_HEADERS }]),
      [200, 201], 'register');

    const tokens = batchWithRetry(emails.map((email) => ['POST', `${API}/auth/login`,
      JSON.stringify({ email, password: '123456' }), { headers: JSON_HEADERS }]),
      [200], 'login').map((r) => r.json('data.accessToken'));

    const addresses = batchWithRetry(tokens.map((token) => ['POST', `${API}/users/me/addresses`,
      JSON.stringify({ recipientName: 'Load Buyer', phone: '0900000000', addressLine: '1 Load St',
        ward: 'W', district: 'D', city: 'HCM', isDefault: true }),
      { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } }]),
      [201], 'create address');

    addresses.forEach((r, k) => users.push({ token: tokens[k], addressId: r.json('data.id') }));
  }

  // Lam nong Feign/gRPC cua order-service (xem docs/Flashsale_Results.md, muc "Phat hien phu"):
  // SKU khong ton tai -> 400, khong dung toi kho.
  const warm = http.post(`${API}/orders`,
    JSON.stringify({ channel: 'WEB', addressId: users[0].addressId, items: [{ skuCode: 'WARMUP-NO-SUCH-SKU', quantity: 1 }] }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${users[0].token}` }, responseCallback: http.expectedStatuses(400) });
  console.log(`setup: warm-up -> ${warm.status} (mong doi 400)`);
  console.log(`setup: ${users.length} user san sang, target ${API}, sku ${SKU}, dinh ${PEAK_RPS} req/s`);
  return { users };
}

export default function (data) {
  const buyer = data.users[Math.floor(Math.random() * data.users.length)];
  const res = http.post(`${API}/orders`,
    JSON.stringify({ channel: 'WEB', addressId: buyer.addressId, items: [{ skuCode: SKU, quantity: 1 }] }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${buyer.token}` }, tags: { name: 'create_order' }, timeout: '10s' });

  if (res.status === 201) {
    created201.add(1);
  } else if (res.status === 409) {
    const code = safeCode(res);
    if (code === 'INVENTORY_BUSY') busy409.add(1);
    else outOfStock409.add(1);
  } else if (res.status === 429) {
    rateLimit429.add(1);
  } else {
    otherStatus.add(1);
    if (Math.random() < 0.05) {  // in mau 5% de khong ngap log khi loi hang loat
      console.error(`unexpected ${res.status}: ${String(res.body).slice(0, 200)}`);
    }
  }

  check(res, { 'status 201 hoac 409': (r) => r.status === 201 || r.status === 409 });
}

function safeCode(res) {
  try { return res.json('code'); } catch (e) { return null; }
}
