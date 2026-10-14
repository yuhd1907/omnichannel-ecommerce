import { FiCheck, FiX } from "react-icons/fi";
import type { Order } from "@/interface/order.interface";
import type { Payment } from "@/interface/payment.interface";

type StepState = "done" | "current" | "failed" | "todo";

interface Step {
  title: string;
  detail: string;
  state: StepState;
}

/** Suy ra từng bước của Saga đặt hàng từ trạng thái order + payment hiện tại. */
function buildSteps(order: Order, payment: Payment | null | undefined): Step[] {
  const s = order.status;
  const fulfilled = s === "CONFIRMED" || s === "SHIPPING" || s === "COMPLETED";
  const cancelled = s === "CANCELLED";

  const cod = order.paymentMethod === "COD" || payment?.method === "COD";
  const paymentStep: StepState = cod
    ? (fulfilled ? "done" : cancelled ? "todo" : "current")
    :
    payment?.status === "SUCCESS" ? "done" : payment?.status === "FAILED" ? "failed" : cancelled ? "todo" : payment ? "current" : "todo";

  return [
    {
      title: "Tạo đơn & giữ kho",
      detail: "order-service · Redisson lock + UPDATE có điều kiện, ghi outbox order.created",
      state: "done",
    },
    {
      title: "Khởi tạo thanh toán",
      detail: "Outbox → RabbitMQ order.events → payment-service tạo payment",
      state: payment ? "done" : cancelled ? "todo" : "current",
    },
    cod
      ? {
          title: "Chọn thanh toán khi nhận hàng",
          detail: "payment-service ghi COD + outbox payment.cod_accepted",
          state: paymentStep,
        }
      : {
          title: "Cổng thanh toán trả kết quả",
          detail: payment?.method ? `${payment.method}: IPN/webhook có chữ ký · chống lặp theo gateway_txn_id` : "IPN/webhook có chữ ký · chống lặp theo gateway_txn_id",
          state: paymentStep,
        },
    cancelled
      ? {
          title: "Hủy đơn & trả hàng về kho",
          detail: "payment.failed / người dùng hủy / quá 15 phút → order.cancelled",
          state: "failed",
        }
      : {
          title: "Xác nhận đơn",
          detail: cod ? "payment.cod_accepted → order-service: CONFIRMED (chưa thu tiền), trừ kho đã giữ" : "payment.succeeded → order-service: CONFIRMED, trừ kho đã giữ",
          state: fulfilled ? "done" : paymentStep === "done" ? "current" : "todo",
        },
    ...(cancelled
      ? []
      : ([
          { title: "Đang giao hàng", detail: "Admin chuyển CONFIRMED → SHIPPING", state: s === "SHIPPING" ? "current" : s === "COMPLETED" ? "done" : "todo" },
          { title: cod ? "Giao hàng & thu tiền" : "Hoàn thành", detail: cod ? "Admin chuyển COMPLETED → đơn PAID, payment COD SUCCESS" : "Admin chuyển SHIPPING → COMPLETED", state: s === "COMPLETED" ? "done" : "todo" },
        ] as Step[])),
  ];
}

const DOT: Record<StepState, string> = {
  done: "bg-neutral-900 text-white",
  current: "bg-white text-neutral-900 ring-2 ring-neutral-900 animate-pulse",
  failed: "bg-rose-600 text-white",
  todo: "bg-neutral-100 text-neutral-400 ring-1 ring-neutral-200",
};

export function SagaTimeline({ order, payment }: { order: Order; payment: Payment | null | undefined }) {
  const steps = buildSteps(order, payment);
  return (
    <ol className="relative">
      {steps.map((step, i) => (
        <li key={step.title} className="relative flex gap-4 pb-6 last:pb-0">
          {i < steps.length - 1 && <span className="absolute top-7 left-[13px] h-[calc(100%-1.75rem)] w-px bg-neutral-200" />}
          <span className={`relative z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold ${DOT[step.state]}`}>
            {step.state === "done" ? <FiCheck /> : step.state === "failed" ? <FiX /> : i + 1}
          </span>
          <div className="pt-0.5">
            <div className={`text-sm font-semibold ${step.state === "todo" ? "text-neutral-500" : "text-neutral-900"}`}>{step.title}</div>
            <div className="mt-0.5 text-xs leading-relaxed text-neutral-500">{step.detail}</div>
          </div>
        </li>
      ))}
    </ol>
  );
}
