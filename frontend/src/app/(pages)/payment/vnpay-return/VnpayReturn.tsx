"use client";

import { useSearchParams } from "next/navigation";
import { FiCheckCircle, FiXCircle } from "react-icons/fi";
import { LinkButton } from "@/app/components/button/Button";
import { EmptyBlock, ErrorBlock, LoadingBlock } from "@/app/components/card/States";
import { api } from "@/helper/api";
import { useAsync } from "@/hooks/useAsync";

interface ReturnResult {
  orderId: string | null;
  result: string;
  responseCode: string | null;
}

/** Chuyển nguyên query vnp_* (kèm vnp_SecureHash) cho payment-service kiểm chữ ký và áp kết quả. */
export function VnpayReturn() {
  const params = useSearchParams();
  const query = params.toString();
  const { data, error, reload } = useAsync(() => api<ReturnResult>(`/payments/vnpay/return?${query}`, { auth: false }), [query]);

  if (error && !data) return <ErrorBlock error={error} onRetry={reload} />;
  if (!data) return <LoadingBlock label="Đang xác nhận thanh toán..." />;

  const ok = data.result === "SUCCESS";
  return (
    <EmptyBlock
      icon={ok ? <FiCheckCircle className="text-emerald-600" /> : <FiXCircle className="text-rose-600" />}
      title={ok ? "Thanh toán VNPAY thành công" : "Thanh toán VNPAY không thành công"}
    >
      <p>{ok ? "Đơn hàng sẽ được xác nhận trong giây lát." : `Mã kết quả: ${data.responseCode ?? data.result}`}</p>
      {data.orderId && (
        <LinkButton href={`/orders/${data.orderId}`} className="mt-4">
          Xem đơn hàng
        </LinkButton>
      )}
    </EmptyBlock>
  );
}
