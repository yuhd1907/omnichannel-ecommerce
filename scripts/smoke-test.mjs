// Smoke test end-to-end qua frontend (Node >= 18, khong can cai them gi). Dung de:
//   - lam nong he thong truoc demo (lan goi Feign/gRPC dau tien sau khi khoi dong cham)
//   - kiem tra nhanh sau moi lan deploy
//   - minh hoa Idempotency-Key va webhook co chu ky ma khong can Postman
//
// Chay:  node scripts/smoke-test.mjs                                  (local, frontend :3001)
//        BASE=https://huyecommerce.duckdns.org node scripts/smoke-test.mjs   (VPS)
// Moi lan chay tao 1 user moi va 1 don (mua 1 san pham dau tien con hang).
const BASE = (process.env.BASE || "http://localhost:3001").replace(/\/$/, "");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let failed = 0;

async function call(path, { method = "GET", body, token, headers = {} } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* HTML hoac rong */ }
  return { status: res.status, json, headers: res.headers };
}

function check(step, ok, detail = "") {
  if (!ok) failed++;
  console.log(`${ok ? "OK  " : "FAIL"} ${step.padEnd(40)} ${detail}`);
}

// 1. Trang web + san pham
check("GET / (trang chu)", (await fetch(BASE + "/")).status === 200);
let r = await call("/api/v1/products?page=0&size=50");
check("GET /api/v1/products", r.status === 200, `${r.json?.data?.items?.length ?? 0} san pham`);
const products = r.json?.data?.items ?? [];

// 2. Tai khoan + dia chi
const email = `smoke-${Date.now()}@test.com`;
r = await call("/api/v1/auth/register", { method: "POST", body: { email, password: "123456", fullName: "Smoke Test" } });
check("POST /auth/register", r.status === 201, email);
r = await call("/api/v1/auth/login", { method: "POST", body: { email, password: "123456" } });
check("POST /auth/login", r.status === 200);
const token = r.json?.data?.accessToken;
r = await call("/api/v1/users/me/addresses", {
  method: "POST", token,
  body: { recipientName: "Smoke", phone: "0900000000", addressLine: "1 Test", ward: "W", district: "D", city: "HCM", isDefault: true },
});
check("POST /users/me/addresses", r.status === 201);
const addressId = r.json?.data?.id;

// 3. Dat hang (thu tung SKU toi khi con hang) + gui lai cung Idempotency-Key -> phai tra don cu
let order;
outer: for (const p of products) {
  const detail = (await call(`/api/v1/products/${p.id}`)).json?.data;
  for (const s of detail?.skus ?? []) {
    const key = crypto.randomUUID();
    const body = { channel: "WEB", addressId, items: [{ skuCode: s.skuCode, quantity: 1 }] };
    r = await call("/api/v1/orders", { method: "POST", token, body, headers: { "Idempotency-Key": key } });
    if (r.status !== 201) continue;
    order = r.json.data;
    check("POST /orders", true, `${s.skuCode} -> ${order.status}, traceId=${r.json.traceId}`);
    const again = await call("/api/v1/orders", { method: "POST", token, body, headers: { "Idempotency-Key": key } });
    check("POST /orders cung Idempotency-Key", again.status === 200 && again.json?.data?.id === order.id,
      `${again.status}, Idempotent-Replayed=${again.headers.get("idempotent-replayed")}`);
    break outer;
  }
}
if (!order) {
  check("POST /orders", false, "khong SKU nao con hang (nhap kho o trang Quan tri)");
  process.exit(1);
}

// 4. Thanh toan MOCK: payment-service tao payment khi nhan order.created (Outbox -> RabbitMQ) -> co the 409 vai giay
for (let i = 0; i < 10; i++) {
  r = await call(`/api/v1/orders/${order.id}/payments`, { method: "POST", token, body: { method: "MOCK" } });
  if (!(r.status === 409 && r.json?.code === "PAYMENT_NOT_READY")) break;
  await sleep(1000);
}
check("POST /orders/{id}/payments MOCK", r.status === 201, `amount=${r.json?.data?.amount}`);
r = await call("/api/mock-gateway", { method: "POST", token, body: { orderId: order.id, result: "SUCCESS" } });
check("Webhook MOCK SUCCESS (ky HMAC)", r.status === 200, r.json?.data?.result ?? r.json?.code);

// 5. Saga: payment.succeeded -> order CONFIRMED
let final;
for (let i = 0; i < 15; i++) {
  final = (await call(`/api/v1/orders/${order.id}`, { token })).json?.data;
  if (final?.status !== "PENDING") break;
  await sleep(1000);
}
check("Don sau webhook", final?.status === "CONFIRMED" && final?.paymentStatus === "PAID", `${final?.status} / ${final?.paymentStatus}`);

// 6. Thanh toan khi nhan hang (COD): don xac nhan ngay, chua thu tien
const sku = order.items[0].skuCode;
r = await call("/api/v1/orders", { method: "POST", token, headers: { "Idempotency-Key": crypto.randomUUID() },
  body: { channel: "WEB", addressId, items: [{ skuCode: sku, quantity: 1 }] } });
if (r.status === 201) {
  const codOrder = r.json.data;
  for (let i = 0; i < 10; i++) {
    r = await call(`/api/v1/orders/${codOrder.id}/payments`, { method: "POST", token, body: { method: "COD" } });
    if (!(r.status === 409 && r.json?.code === "PAYMENT_NOT_READY")) break;
    await sleep(1000);
  }
  check("POST /orders/{id}/payments COD", r.status === 201, r.json?.data?.method);
  let cod;
  for (let i = 0; i < 15; i++) {
    cod = (await call(`/api/v1/orders/${codOrder.id}`, { token })).json?.data;
    if (cod?.status !== "PENDING") break;
    await sleep(1000);
  }
  check("Don COD", cod?.status === "CONFIRMED" && cod?.paymentStatus === "UNPAID" && cod?.paymentMethod === "COD",
    `${cod?.status} / ${cod?.paymentStatus} / ${cod?.paymentMethod}`);
} else {
  check("POST /orders (COD)", false, `${r.status} ${r.json?.code} (het hang?)`);
}

// 7. Phan quyen: user thuong goi API admin -> 403
r = await call("/api/v1/admin/orders", { token });
check("GET /admin/orders bang USER", r.status === 403, String(r.status));

console.log(failed ? `\n${failed} buoc FAIL` : `\nTat ca OK. orderId=${order.id}`);
process.exit(failed ? 1 : 0);
