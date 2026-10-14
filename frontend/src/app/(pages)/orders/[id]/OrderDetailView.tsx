"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef } from "react";
import { FiArrowLeft, FiLock, FiMapPin, FiTruck, FiXCircle } from "react-icons/fi";
import { toast } from "sonner";
import { Button } from "@/app/components/button/Button";
import { OrderItemCard } from "@/app/components/card/OrderItemCard";
import { OrderStatusBadge, PaymentStatusBadge } from "@/app/components/card/StatusBadge";
import { EmptyBlock, ErrorBlock, LoadingBlock } from "@/app/components/card/States";
import { ORDER_STATUS } from "@/config/constants";
import { confirmAction } from "@/helper/alert";
import { ApiError, errorMessage } from "@/helper/api";
import { formatCurrency, formatDateTime, shortId } from "@/helper/format";
import { useAsync } from "@/hooks/useAsync";
import { useAuth } from "@/hooks/useAuth";
import type { PaymentMethod } from "@/interface/payment.interface";
import { orderService } from "@/services/order.service";
import { paymentService } from "@/services/payment.service";
import { PaymentPanel } from "./PaymentPanel";
import { SagaTimeline } from "./SagaTimeline";

/** Trong lúc đơn còn PENDING, hỏi lại server định kỳ để thấy Saga chạy (webhook -> event -> order). */
const POLL_MS = 2000;

export function OrderDetailView({ id }: { id: string }) {
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
  return <Detail id={id} />;
}

function Detail({ id }: { id: string }) {
  const params = useSearchParams();
  const preferred = (params.get("pay") as PaymentMethod | null) ?? undefined;

  const order = useAsync(() => orderService.detail(id), [id]);
  // Payment được payment-service tạo khi nhận order.created (qua Outbox + RabbitMQ) -> có thể chưa có ngay
  const payment = useAsync(
    () => paymentService.get(id).catch((e) => (e instanceof ApiError && e.status === 404 ? null : Promise.reject(e))),
    [id],
    !!order.data,
  );

  const status = order.data?.status;
  // Hủy đơn -> payment-service đóng payment bất đồng bộ; hỏi thêm cho tới khi payment hết PENDING
  const shouldPoll = status === "PENDING" || (status === "CANCELLED" && payment.data?.status === "PENDING");
  const { reload: reloadOrder } = order;
  const { reload: reloadPayment } = payment;

  useEffect(() => {
    if (!shouldPoll) return;
    const t = setInterval(() => {
      reloadOrder();
      reloadPayment();
    }, POLL_MS);
    return () => clearInterval(t);
  }, [shouldPoll, reloadOrder, reloadPayment]);

  // Chỉ báo khi Saga đổi trạng thái NGAY lúc đang xem (mở lại đơn cũ thì không báo)
  const prevStatus = useRef(status);
  useEffect(() => {
    if (prevStatus.current === "PENDING" && status === "CONFIRMED") toast.success("Thanh toán thành công — đơn hàng đã được xác nhận");
    if (prevStatus.current === "PENDING" && status === "CANCELLED") toast.warning("Đơn hàng đã bị hủy, hàng giữ đã trả về kho");
    prevStatus.current = status;
  }, [status]);

  if (order.error && !order.data) {
    const notFound = order.error instanceof ApiError && order.error.status === 404;
    return notFound ? <EmptyBlock icon={<FiXCircle />} title="Không tìm thấy đơn hàng" /> : <ErrorBlock error={order.error} onRetry={order.reload} />;
  }
  if (!order.data) return <LoadingBlock label="Đang tải đơn hàng..." />;

  const o = order.data;

  const cancel = async () => {
    if (!(await confirmAction("Hủy đơn hàng?", "Hàng đang giữ sẽ được trả lại kho. Thao tác không hoàn tác được.", "Hủy đơn", true))) return;
    try {
      order.setData(await orderService.cancel(o.id));
      payment.reload();
    } catch (err) {
      toast.error(errorMessage(err));
      order.reload();
    }
  };

  return (
    <div>
      <Link href="/orders" className="mb-6 inline-flex items-center gap-2 text-sm text-neutral-900 hover:text-neutral-500">
        <FiArrowLeft /> Danh sách đơn hàng
      </Link>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-mono text-2xl font-bold text-neutral-900">Đơn #{shortId(o.id)}</h1>
          <div className="mt-1 text-sm text-neutral-500">
            Đặt lúc {formatDateTime(o.createdAt)} · Kênh {o.channel}
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <OrderStatusBadge status={o.status} />
            <PaymentStatusBadge status={o.paymentStatus} method={o.paymentMethod} />
          </div>
          <p className="mt-2 text-sm text-neutral-500">{ORDER_STATUS[o.status]?.desc}</p>
        </div>
        {o.status === "PENDING" && (
          <Button variant="secondary" icon={<FiXCircle />} onClick={cancel}>
            Hủy đơn
          </Button>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          {o.status === "PENDING" && (
            <PaymentPanel
              order={o}
              payment={payment.data}
              preferred={preferred}
              onPaymentChange={(p) => payment.setData(p)}
              onCodAccepted={() => {
                order.reload();
                payment.reload();
              }}
            />
          )}

          {o.paymentMethod === "COD" && o.status !== "CANCELLED" && (
            <section className="card flex items-start gap-3 p-5">
              <FiTruck className="mt-0.5 shrink-0 text-neutral-900" size={20} />
              <div className="text-sm">
                <div className="font-semibold text-neutral-900">Thanh toán khi nhận hàng</div>
                <div className="text-neutral-500">
                  {o.paymentStatus === "PAID"
                    ? `Đã thu ${formatCurrency(o.totalAmount)} khi giao hàng.`
                    : `Chuẩn bị ${formatCurrency(o.totalAmount)} tiền mặt để trả cho nhân viên giao hàng.`}
                </div>
              </div>
            </section>
          )}

          <section className="card p-6">
            <h2 className="mb-4 font-semibold text-neutral-900">Sản phẩm</h2>
            <div className="space-y-2.5">
              {o.items.map((it) => (
                <OrderItemCard key={it.id} item={it} />
              ))}
            </div>
            <div className="mt-5 flex justify-between border-t border-neutral-200 pt-4 text-lg font-bold text-neutral-900">
              <span>Tổng cộng</span>
              <span className="text-neutral-900">{formatCurrency(o.totalAmount)}</span>
            </div>
          </section>

          <section className="card p-6">
            <h2 className="mb-3 flex items-center gap-2 font-semibold text-neutral-900">
              <FiMapPin className="text-neutral-900" /> Giao tới
            </h2>
            <div className="text-sm text-neutral-700">
              <div className="font-semibold">
                {o.shippingRecipientName} · {o.shippingPhone}
              </div>
              <div className="text-neutral-500">{o.shippingAddress}</div>
            </div>
          </section>
        </div>

        <aside className="card h-fit p-6 lg:sticky lg:top-24">
          <h2 className="mb-1 font-semibold text-neutral-900">Theo dõi Saga</h2>
          <p className="mb-5 text-xs text-neutral-500">Cập nhật tự động mỗi {POLL_MS / 1000} giây khi đơn còn chờ.</p>
          <SagaTimeline order={o} payment={payment.data} />
        </aside>
      </div>
    </div>
  );
}
