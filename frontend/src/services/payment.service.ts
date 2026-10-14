import { api } from "@/helper/api";
import type { Payment, PaymentMethod } from "@/interface/payment.interface";

export const paymentService = {
  get: (orderId: string) => api<Payment>(`/orders/${orderId}/payments`),

  /** Idempotent khi payment còn PENDING: gọi lại trả đúng payment đó (có thể đổi method). */
  checkout: (orderId: string, method: PaymentMethod) =>
    api<Payment>(`/orders/${orderId}/payments`, { method: "POST", body: { method } }),
};
