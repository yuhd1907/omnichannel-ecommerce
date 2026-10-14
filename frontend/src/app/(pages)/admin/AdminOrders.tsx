"use client";

import Link from "next/link";
import { useState } from "react";
import { FiCheckCircle, FiChevronLeft, FiChevronRight, FiInbox, FiTruck } from "react-icons/fi";
import { toast } from "sonner";
import { Button } from "@/app/components/button/Button";
import { OrderStatusBadge, PaymentStatusBadge } from "@/app/components/card/StatusBadge";
import { EmptyBlock, ErrorBlock, LoadingBlock } from "@/app/components/card/States";
import { ORDER_STATUS } from "@/config/constants";
import { confirmAction } from "@/helper/alert";
import { errorMessage } from "@/helper/api";
import { formatCurrency, formatDateTime, shortId } from "@/helper/format";
import { useAsync } from "@/hooks/useAsync";
import type { OrderStatus, OrderSummary } from "@/interface/order.interface";
import { adminService } from "@/services/order.service";

const FILTERS: (OrderStatus | "")[] = ["", "CONFIRMED", "SHIPPING", "PENDING", "COMPLETED", "CANCELLED"];

/** Chỉ hai bước admin được phép (OrderStateService.advanceFulfillment). */
const NEXT: Partial<Record<OrderStatus, { to: "SHIPPING" | "COMPLETED"; label: string; icon: React.ReactNode }>> = {
  CONFIRMED: { to: "SHIPPING", label: "Giao hàng", icon: <FiTruck /> },
  SHIPPING: { to: "COMPLETED", label: "Hoàn tất", icon: <FiCheckCircle /> },
};

export function AdminOrders() {
  const [status, setStatus] = useState<OrderStatus | "">("CONFIRMED");
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const { data, error, loading, reload } = useAsync(() => adminService.orders(status, page), [status, page]);

  const advance = async (o: OrderSummary) => {
    const next = NEXT[o.status];
    if (!next) return;
    const ok = await confirmAction(
      `${next.label} đơn #${shortId(o.id)}?`,
      `${ORDER_STATUS[o.status].label} → ${ORDER_STATUS[next.to].label}. Khách sẽ nhận email thông báo.`,
      next.label,
    );
    if (!ok) return;
    setBusyId(o.id);
    try {
      await adminService.advance(o.id, next.to);
      toast.success(`Đơn #${shortId(o.id)}: ${ORDER_STATUS[next.to].label}`);
    } catch (err) {
      // 409: admin khác vừa chuyển trước (UPDATE ... WHERE status = :from chỉ một người thắng)
      toast.error(errorMessage(err));
    } finally {
      setBusyId(null);
      reload();
    }
  };

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        {FILTERS.map((f) => (
          <button
            key={f || "ALL"}
            onClick={() => {
              setStatus(f);
              setPage(0);
            }}
            className={`rounded-full px-3.5 py-1.5 text-xs font-semibold transition ${
              status === f ? "border border-neutral-900 bg-neutral-900 text-white" : "border border-neutral-300 bg-white text-neutral-900 hover:border-neutral-400 active:shadow-[0_8px_18px_-6px_rgba(0,0,0,0.28)]"
            }`}
          >
            {f ? ORDER_STATUS[f].label : "Tất cả"}
          </button>
        ))}
        <Button variant="ghost" size="sm" className="ml-auto" onClick={reload} loading={loading && !!data}>
          Làm mới
        </Button>
      </div>

      {error && !data ? (
        <ErrorBlock error={error} onRetry={reload} />
      ) : !data ? (
        <LoadingBlock label="Đang tải đơn hàng..." />
      ) : data.items.length === 0 ? (
        <EmptyBlock icon={<FiInbox />} title="Không có đơn nào ở trạng thái này" />
      ) : (
        <>
          <div className={`card overflow-x-auto transition ${loading ? "opacity-60" : ""}`}>
            <table className="w-full min-w-[720px] text-sm">
              <thead className="border-b border-neutral-200 text-left text-xs tracking-wide text-neutral-500 uppercase">
                <tr>
                  <th className="px-5 py-3 font-medium">Mã đơn</th>
                  <th className="px-5 py-3 font-medium">Ngày đặt</th>
                  <th className="px-5 py-3 font-medium">Kênh</th>
                  <th className="px-5 py-3 font-medium">Trạng thái</th>
                  <th className="px-5 py-3 font-medium">Thanh toán</th>
                  <th className="px-5 py-3 text-right font-medium">Tổng tiền</th>
                  <th className="px-5 py-3 text-right font-medium">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-200">
                {data.items.map((o) => {
                  const next = NEXT[o.status];
                  return (
                    <tr key={o.id} className="hover:bg-neutral-50">
                      <td className="px-5 py-3.5">
                        <Link href={`/orders/${o.id}`} className="font-mono font-semibold text-neutral-800 hover:text-neutral-500">
                          #{shortId(o.id)}
                        </Link>
                      </td>
                      <td className="px-5 py-3.5 text-neutral-500">{formatDateTime(o.createdAt)}</td>
                      <td className="px-5 py-3.5 text-neutral-500">{o.channel}</td>
                      <td className="px-5 py-3.5">
                        <OrderStatusBadge status={o.status} />
                      </td>
                      <td className="px-5 py-3.5">
                        <PaymentStatusBadge status={o.paymentStatus} method={o.paymentMethod} />
                      </td>
                      <td className="px-5 py-3.5 text-right font-semibold text-neutral-900">{formatCurrency(o.totalAmount)}</td>
                      <td className="px-5 py-3.5 text-right">
                        {next ? (
                          <Button size="sm" icon={next.icon} loading={busyId === o.id} disabled={!!busyId} onClick={() => advance(o)}>
                            {next.label}
                          </Button>
                        ) : (
                          <span className="text-xs text-neutral-400">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="mt-5 flex items-center justify-center gap-3 text-sm text-neutral-500">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)} icon={<FiChevronLeft />}>
                Trước
              </Button>
              <span>
                Trang {page + 1} / {data.totalPages} · {data.totalElements} đơn
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Sau <FiChevronRight />
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
