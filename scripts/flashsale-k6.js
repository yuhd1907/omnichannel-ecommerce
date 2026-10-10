// Flash sale: 100 nguoi cung luc, moi nguoi mua 1 FLASH-001; kho chi co 10.
//
// Chay (tu thu muc goc repo, PowerShell):
//   Get-Content scripts\flashsale-reset.sql | docker exec -i ecommerce-postgres psql -U postgres -d order_db
//   Get-Content scripts\flashsale-k6.js | docker run --rm -i --network host grafana/k6 run -
// Docker Desktop Windows: neu --network host khong toi duoc localhost:
//   Get-Content scripts\flashsale-k6.js | docker run --rm -i -e HOST=host.docker.internal grafana/k6 run -
//
// Goi THANG order-service (:8083) va identity-service (:8081), khong qua gateway: rate limit
// cua gateway se chan 100 request cung luc truoc khi toi phan can do (lock + conditional UPDATE).
//
// Tieu chi dat (kiem them bang SQL trong flashsale-reset.sql): dung 10 x 201, 0 ma khac 201/409.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const HOST         = __ENV.HOST || 'localhost';
const ORDER_URL    = __ENV.ORDER_URL || `http://${HOST}:8083`;
const IDENTITY_URL = __ENV.IDENTITY_URL || `http://${HOST}:8081`;
const SKU          = __ENV.SKU || 'FLASH-001';
const BUYERS       = Number(__ENV.BUYERS || 100);
const STOCK        = Number(__ENV.STOCK || 10);

const created201    = new Counter('created_201');
const outOfStock409 = new Counter('out_of_stock_409');
const busy409       = new Counter('inventory_busy_409');
const otherStatus   = new Counter('other_status');

export const options = {
  setupTimeout: '180s',
  scenarios: {
    flash_sale: {
      executor: 'shared-iterations',
      vus: BUYERS,
      iterations: BUYERS,   // moi nguoi mua dung 1 lan
      maxDuration: '60s',
    },
  },
  thresholds: {
    created_201: [`count==${STOCK}`],          // dung 10 don, khong hon khong kem
    other_status: ['count==0'],                // khong 500, khong ma la
    'http_req_duration{name:create_order}': ['p(95)<10000'], // de summary in p95 rieng cho POST /orders
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Tao BUYERS user that (moi user co token + dia chi rieng). POST /orders yeu cau addressId thuoc user.
export function setup() {
  const run = Date.now();
  const users = [];
  const BATCH = 20;

  for (let start = 0; start < BUYERS; start += BATCH) {
    const idx = [];
    for (let i = start; i < Math.min(start + BATCH, BUYERS); i++) idx.push(i);
    const emails = idx.map((i) => `flash-${run}-${i}@test.com`);

    http.batch(emails.map((email) => ['POST', `${IDENTITY_URL}/api/v1/auth/register`,
      JSON.stringify({ email, password: '123456', fullName: 'Flash Buyer' }), { headers: JSON_HEADERS }]));

    const logins = http.batch(emails.map((email) => ['POST', `${IDENTITY_URL}/api/v1/auth/login`,
      JSON.stringify({ email, password: '123456' }), { headers: JSON_HEADERS }]));
    const tokens = logins.map((r) => {
      if (r.status !== 200) throw new Error(`login failed ${r.status}: ${r.body}`);
      return r.json('data.accessToken');
    });

    const addresses = http.batch(tokens.map((token) => ['POST', `${IDENTITY_URL}/api/v1/users/me/addresses`,
      JSON.stringify({ recipientName: 'Flash Buyer', phone: '0900000000', addressLine: '1 Flash St',
        ward: 'W', district: 'D', city: 'HCM', isDefault: true }),
      { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } }]));
    addresses.forEach((r, k) => {
      if (r.status !== 201) throw new Error(`create address failed ${r.status}: ${r.body}`);
      users.push({ token: tokens[k], addressId: r.json('data.id') });
    });
  }
  // Lam nong order-service: Feign (Spring Cloud OpenFeign) khoi tao HttpMessageConverters luoi va
  // KHONG thread-safe -> neu loat 100 request la loi goi Feign dau tien sau khi khoi dong, mot phan
  // nhan 503 "'messageConverters' must not be empty". Goi 1 don voi SKU khong ton tai: Feign lay dia
  // chi chay truoc buoc kiem SKU -> duoc khoi tao, roi tra 400, khong dung toi kho.
  const warm = http.post(`${ORDER_URL}/api/v1/orders`,
    JSON.stringify({ channel: 'WEB', addressId: users[0].addressId, items: [{ skuCode: 'WARMUP-NO-SUCH-SKU', quantity: 1 }] }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${users[0].token}` } });
  console.log(`setup: warm-up order call -> ${warm.status} (expect 400 SKU_UNAVAILABLE)`);

  console.log(`setup: ${users.length} buyers ready, target ${ORDER_URL}, sku ${SKU}`);
  return { users };
}

export default function (data) {
  // shared-iterations: iterationInTest chay 0..BUYERS-1 -> moi iteration la 1 nguoi mua khac nhau.
  const buyer = data.users[exec.scenario.iterationInTest % data.users.length];
  const res = http.post(`${ORDER_URL}/api/v1/orders`,
    JSON.stringify({ channel: 'WEB', addressId: buyer.addressId, items: [{ skuCode: SKU, quantity: 1 }] }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${buyer.token}` }, tags: { name: 'create_order' } });

  const code = res.status === 409 ? safeCode(res) : null;
  if (res.status === 201) created201.add(1);
  else if (code === 'OUT_OF_STOCK') outOfStock409.add(1);
  else if (code === 'INVENTORY_BUSY') busy409.add(1);
  else {
    otherStatus.add(1);
    console.error(`unexpected ${res.status} ${code || ''}: ${String(res.body).slice(0, 200)}`);
  }

  check(res, { 'status la 201 hoac 409': (r) => r.status === 201 || r.status === 409 });
}

function safeCode(res) {
  try { return res.json('code'); } catch (e) { return null; }
}
