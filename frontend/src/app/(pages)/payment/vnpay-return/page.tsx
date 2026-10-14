import { Suspense } from "react";
import { LoadingBlock } from "@/app/components/card/States";
import { VnpayReturn } from "./VnpayReturn";

// VNPAY_RETURN_URL của payment-service trỏ về trang này (vd https://<domain>/payment/vnpay-return).
export default function VnpayReturnPage() {
  return (
    <Suspense fallback={<LoadingBlock label="Đang xác nhận thanh toán..." />}>
      <VnpayReturn />
    </Suspense>
  );
}
