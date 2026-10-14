// payment-service: PaymentView

/** MOCK chỉ còn cho smoke test (scripts/smoke-test.mjs), giao diện dùng COD và VNPAY. */
export type PaymentMethod = "COD" | "VNPAY" | "MOCK";

export interface Payment {
  paymentId: string;
  orderId: string;
  amount: number;
  method: PaymentMethod | null;
  status: "PENDING" | "SUCCESS" | "FAILED";
  checkoutUrl: string | null;
}
