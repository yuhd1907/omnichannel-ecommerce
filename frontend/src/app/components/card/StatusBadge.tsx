import { ORDER_STATUS, PAYMENT_STATUS, TONE_CLASS } from "@/config/constants";
import type { OrderStatus, PaymentStatus } from "@/interface/order.interface";

function Badge({ label, tone }: { label: string; tone: keyof typeof TONE_CLASS }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${TONE_CLASS[tone]}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {label}
    </span>
  );
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const info = ORDER_STATUS[status] ?? { label: status, tone: "slate" as const };
  return <Badge label={info.label} tone={info.tone} />;
}

export function PaymentStatusBadge({ status, method }: { status: PaymentStatus; method?: string | null }) {
  if (method === "COD" && status === "UNPAID") return <Badge label="Thu tiền khi giao (COD)" tone="sky" />;
  const info = PAYMENT_STATUS[status] ?? { label: status, tone: "slate" as const };
  return <Badge label={info.label} tone={info.tone} />;
}
