"use client";

import { useEffect, useRef, useState } from "react";
import { FiClock, FiCreditCard, FiExternalLink, FiTruck } from "react-icons/fi";
import { toast } from "sonner";
import { Button, choiceClass } from "@/app/components/button/Button";
import { RESERVATION_TTL_MINUTES } from "@/config/constants";
import { ApiError, errorMessage } from "@/helper/api";
import { formatCountdown, formatCurrency } from "@/helper/format";
import type { Order } from "@/interface/order.interface";
import type { Payment, PaymentMethod } from "@/interface/payment.interface";
import { paymentService } from "@/services/payment.service";

const METHODS: { id: PaymentMethod; label: string; desc: string; icon: React.ReactNode }[] = [
  { id: "COD", label: "Thanh toán khi nhận hàng", desc: "Trả tiền mặt cho nhân viên giao hàng", icon: <FiTruck /> },
  { id: "VNPAY", label: "VNPAY", desc: "Thẻ ATM, Internet Banking hoặc quét VNPAY-QR", icon: <FiCreditCard /> },
];

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** order.created đi qua Outbox -> RabbitMQ -> payment-service mất ~1-2 giây; 409 PAYMENT_NOT_READY thì thử lại. */
async function checkoutWithRetry(orderId: string, method: PaymentMethod) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await paymentService.checkout(orderId, method);
    } catch (err) {
      if (err instanceof ApiError && err.code === "PAYMENT_NOT_READY" && attempt < 6) {
        await sleep(1000);
        continue;
      }
      throw err;
    }
  }
}

function initialMethod(preferred?: PaymentMethod, current?: string | null): PaymentMethod {
  if (preferred === "COD" || preferred === "VNPAY") return preferred;
  return current === "VNPAY" ? "VNPAY" : "COD";
}

export function PaymentPanel({
  order,
  payment,
  preferred,
  onPaymentChange,
  onCodAccepted,
}: {
  order: Order;
  payment: Payment | null | undefined;
  preferred?: PaymentMethod;
  onPaymentChange: (p: Payment) => void;
  onCodAccepted: () => void;
}) {
  const [method, setMethod] = useState<PaymentMethod>(initialMethod(preferred, payment?.method));
  const [busy, setBusy] = useState(false);
  const [codSent, setCodSent] = useState(false);

  const start = async (m: PaymentMethod) => {
    setBusy(true);
    try {
      const p = await checkoutWithRetry(order.id, m);
      onPaymentChange(p);
      if (m === "VNPAY") {
        if (!p.checkoutUrl) throw new Error("Server không trả về URL thanh toán VNPAY");
        window.location.href = p.checkoutUrl;
        return;
      }
      // COD: payment-service phát payment.cod_accepted -> order-service xác nhận đơn sau 1-2 giây
      setCodSent(true);
      onCodAccepted();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  // Từ trang đặt hàng chuyển sang (?pay=COD|VNPAY): thực hiện luôn phương thức đã chọn
  const autoStarted = useRef(false);
  useEffect(() => {
    if ((preferred === "COD" || preferred === "VNPAY") && !autoStarted.current) {
      autoStarted.current = true;
      void start(preferred);
    }
    // chỉ chạy một lần khi vào trang
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <section className="card overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-neutral-200 bg-neutral-50 px-6 py-4">
        <h2 className="flex items-center gap-2 font-semibold text-neutral-900">
          <FiCreditCard /> Thanh toán đơn hàng
        </h2>
        <Countdown createdAt={order.createdAt} />
      </div>

      <div className="p-6">
        {codSent || payment?.method === "COD" ? (
          <p className="flex items-center gap-2 text-sm text-neutral-700">
            <FiTruck /> Đã chọn thanh toán khi nhận hàng, đang xác nhận đơn...
          </p>
        ) : (
          <>
            <div className="label">Phương thức</div>
            <div className="mb-5 grid gap-3 sm:grid-cols-2">
              {METHODS.map((m) => (
                <button key={m.id} onClick={() => setMethod(m.id)} className={`p-4 ${choiceClass(method === m.id)}`}>
                  <div className="flex items-center gap-2 font-semibold text-neutral-900">
                    {m.icon} {m.label}
                  </div>
                  <div className="mt-1 text-xs text-neutral-500">{m.desc}</div>
                </button>
              ))}
            </div>
            <Button size="lg" loading={busy} onClick={() => start(method)} icon={method === "VNPAY" ? <FiExternalLink /> : <FiTruck />}>
              {method === "COD"
                ? `Xác nhận đặt hàng · trả ${formatCurrency(order.totalAmount)} khi nhận`
                : `Thanh toán ${formatCurrency(order.totalAmount)} qua VNPAY`}
            </Button>
          </>
        )}
      </div>
    </section>
  );
}

function Countdown({ createdAt }: { createdAt: string }) {
  const deadline = new Date(createdAt).getTime() + RESERVATION_TTL_MINUTES * 60_000;
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const left = deadline - now;
  return (
    <span className={`flex items-center gap-1.5 font-mono text-sm font-semibold ${left < 120_000 ? "text-rose-700" : "text-amber-700"}`}>
      <FiClock />
      {left > 0 ? `Giữ hàng còn ${formatCountdown(left)}` : "Đã hết hạn giữ hàng, đơn sắp tự hủy"}
    </span>
  );
}
