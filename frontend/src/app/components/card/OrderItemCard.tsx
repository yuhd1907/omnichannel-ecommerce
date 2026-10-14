import { formatCurrency } from "@/helper/format";
import type { OrderItem } from "@/interface/order.interface";

export function OrderItemCard({ item }: { item: OrderItem }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-3">
      <div className="min-w-0">
        <div className="truncate font-medium text-neutral-900">{item.productName}</div>
        <div className="font-mono text-xs text-neutral-500">{item.skuCode}</div>
      </div>
      <div className="shrink-0 text-right text-sm">
        <div className="text-neutral-500">
          {formatCurrency(item.unitPrice)} × {item.quantity}
        </div>
        <div className="font-semibold text-neutral-900">{formatCurrency(item.subtotal)}</div>
      </div>
    </div>
  );
}
