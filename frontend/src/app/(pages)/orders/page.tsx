"use client";

import Link from "next/link";
import { useState } from "react";
import { FiChevronLeft, FiChevronRight, FiLock, FiPackage } from "react-icons/fi";
import { Button, LinkButton } from "@/app/components/button/Button";
import { OrderStatusBadge, PaymentStatusBadge } from "@/app/components/card/StatusBadge";
import { EmptyBlock, ErrorBlock, LoadingBlock, PageTitle } from "@/app/components/card/States";
import { formatCurrency, formatDateTime, shortId } from "@/helper/format";
import { useAsync } from "@/hooks/useAsync";
import { useAuth } from "@/hooks/useAuth";
import { orderService } from "@/services/order.service";

const PAGE_SIZE = 10;

export default function OrdersPage() {
  const { user, hydrated, openLogin } = useAuth();
  if (!hydrated) return <LoadingBlock />;
  if (!user) {
    return (
      <EmptyBlock icon={<FiLock />} title="Đăng nhập để xem đơn hàng">
        <Button className="mt-2" onClick={openLogin}>
          Đăng nhập
        </Button>
      </EmptyBlock>
    );
  }
  return <OrderList />;
}

function OrderList() {
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useAsync(() => orderService.mine(page, PAGE_SIZE), [page]);

  return (
    <div>
      <PageTitle
        title="Đơn hàng của tôi"
        subtitle={data ? `${data.totalElements} đơn hàng` : undefined}
        action={
          <Button variant="secondary" size="sm" onClick={reload} loading={loading && !!data}>
            Làm mới
          </Button>
        }
      />

      {error && !data ? (
        <ErrorBlock error={error} onRetry={reload} />
      ) : !data ? (
        <LoadingBlock label="Đang tải đơn hàng..." />
      ) : data.items.length === 0 ? (
        <EmptyBlock icon={<FiPackage />} title="Bạn chưa có đơn hàng nào">
          <LinkButton href="/" className="mt-2">
            Mua sắm ngay
          </LinkButton>
        </EmptyBlock>
      ) : (
        <>
          <div className="card overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="border-b border-neutral-200 text-left text-xs tracking-wide text-neutral-500 uppercase">
                <tr>
                  <th className="px-5 py-3 font-medium">Mã đơn</th>
                  <th className="px-5 py-3 font-medium">Ngày đặt</th>
                  <th className="px-5 py-3 font-medium">Trạng thái</th>
                  <th className="px-5 py-3 font-medium">Thanh toán</th>
                  <th className="px-5 py-3 text-right font-medium">Tổng tiền</th>
                  <th className="px-5 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-200">
                {data.items.map((o) => (
                  <tr key={o.id} className="transition hover:bg-neutral-50">
                    <td className="px-5 py-4 font-mono font-semibold text-neutral-800">#{shortId(o.id)}</td>
                    <td className="px-5 py-4 text-neutral-500">{formatDateTime(o.createdAt)}</td>
                    <td className="px-5 py-4">
                      <OrderStatusBadge status={o.status} />
                    </td>
                    <td className="px-5 py-4">
                      <PaymentStatusBadge status={o.paymentStatus} method={o.paymentMethod} />
                    </td>
                    <td className="px-5 py-4 text-right font-semibold text-neutral-900">{formatCurrency(o.totalAmount)}</td>
                    <td className="px-5 py-4 text-right">
                      <Link href={`/orders/${o.id}`} className="font-semibold text-neutral-900 hover:text-neutral-500">
                        {o.status === "PENDING" ? "Thanh toán →" : "Chi tiết →"}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="mt-6 flex items-center justify-center gap-3 text-sm text-neutral-500">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)} icon={<FiChevronLeft />}>
                Trước
              </Button>
              <span>
                Trang {page + 1} / {data.totalPages}
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
