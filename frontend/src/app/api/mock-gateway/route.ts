import { createHmac, randomUUID } from "node:crypto";
import type { NextRequest } from "next/server";

// Đóng vai cổng thanh toán MOCK: người mua bấm "Thành công/Thất bại" -> server này ký HMAC-SHA256
// (cùng secret với payment-service) rồi gọi webhook như một cổng thật. Secret chỉ nằm ở server Next.
//
// Không tin client: số tiền và quyền sở hữu lấy từ GET /orders/{id}/payments bằng token của người gọi
// (người khác gọi -> 403/404 từ payment-service), client chỉ chọn kết quả.

const GATEWAY_URL = (process.env.GATEWAY_URL ?? "http://localhost:8080").replace(/\/$/, "");
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function webhookSecret(): string | null {
  if (process.env.PAYMENT_WEBHOOK_SECRET) return process.env.PAYMENT_WEBHOOK_SECRET;
  // Giá trị mặc định của docker-compose.yml, chỉ dùng khi chạy dev
  return process.env.NODE_ENV === "production" ? null : "dev-webhook-secret-key-12345";
}

function error(status: number, code: string, message: string) {
  return Response.json({ code, message, data: null, errors: null, traceId: null, timestamp: new Date().toISOString() }, { status });
}

/** Chuyển nguyên status + body của backend về client (giữ code lỗi chuẩn ApiError). */
async function passThrough(res: Response) {
  return new Response(await res.text(), {
    status: res.status,
    headers: { "Content-Type": res.headers.get("Content-Type") ?? "application/json" },
  });
}

export async function POST(request: NextRequest) {
  const authorization = request.headers.get("authorization");
  if (!authorization) return error(401, "UNAUTHORIZED", "Missing access token");

  const secret = webhookSecret();
  if (!secret) return error(503, "PAYMENT_GATEWAY_NOT_CONFIGURED", "PAYMENT_WEBHOOK_SECRET is not set");

  let orderId: unknown;
  let result: unknown;
  try {
    ({ orderId, result } = await request.json());
  } catch {
    return error(400, "VALIDATION_FAILED", "Body must be JSON {orderId, result}");
  }
  if (typeof orderId !== "string" || !UUID_RE.test(orderId)) return error(400, "VALIDATION_FAILED", "orderId must be a UUID");
  if (result !== "SUCCESS" && result !== "FAILED") return error(400, "VALIDATION_FAILED", "result must be SUCCESS or FAILED");

  const paymentRes = await fetch(`${GATEWAY_URL}/api/v1/orders/${orderId}/payments`, {
    headers: { Authorization: authorization, Accept: "application/json" },
    cache: "no-store",
  });
  if (!paymentRes.ok) return passThrough(paymentRes);

  const payment = (await paymentRes.json()).data as { amount: number; method: string | null; status: string };
  if (payment.method !== "MOCK") {
    return error(409, "PAYMENT_METHOD_MISMATCH", "Payment is not using the MOCK gateway");
  }

  // Ký đúng chuỗi byte gửi đi: payment-service tính HMAC trên body thô, hex chữ thường
  const body = JSON.stringify({ orderId, gatewayTxnId: `MOCK-${randomUUID()}`, status: result, amount: payment.amount });
  const signature = createHmac("sha256", secret).update(body, "utf8").digest("hex");

  const webhookRes = await fetch(`${GATEWAY_URL}/api/v1/webhooks/payments/mock`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Signature": signature },
    body,
    cache: "no-store",
  });
  return passThrough(webhookRes);
}
