import { api, apiRequest } from "@/helper/api";
import type { PageData } from "@/interface/api.interface";
import type { CreateOrderRequest, Inventory, Order, OrderStatus, OrderSummary } from "@/interface/order.interface";

export const orderService = {
  /**
   * Idempotency-Key: một key cho mỗi lần bấm "Đặt hàng"; gửi lại đúng key khi retry (mất mạng, timeout)
   * -> server trả lại đơn đã tạo (200 + Idempotent-Replayed) thay vì giữ kho lần 2.
   */
  create: async (req: CreateOrderRequest, idempotencyKey: string) => {
    const res = await apiRequest<Order>("/orders", {
      method: "POST",
      body: req,
      headers: { "Idempotency-Key": idempotencyKey },
    });
    return { order: res.data, replayed: res.headers.get("Idempotent-Replayed") === "true" };
  },

  mine: (page = 0, size = 10) => api<PageData<OrderSummary>>(`/orders?page=${page}&size=${size}`),

  detail: (id: string) => api<Order>(`/orders/${id}`),

  cancel: (id: string) => api<Order>(`/orders/${id}/cancel`, { method: "POST" }),
};

export const adminService = {
  orders: (status: OrderStatus | "", page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status);
    return api<PageData<OrderSummary>>(`/admin/orders?${params}`);
  },

  /** Chỉ CONFIRMED -> SHIPPING và SHIPPING -> COMPLETED; hai admin bấm cùng lúc thì một người nhận 409. */
  advance: (id: string, status: "SHIPPING" | "COMPLETED") =>
    api<Order>(`/admin/orders/${id}/status`, { method: "PATCH", body: { status } }),

  inventory: (skuCode: string) => api<Inventory>(`/admin/inventory/${encodeURIComponent(skuCode)}`),

  /** Upsert: SKU chưa có dòng tồn kho thì server tạo mới. */
  adjust: (skuCode: string, delta: number, reason: string) =>
    api<Inventory>(`/admin/inventory/${encodeURIComponent(skuCode)}/adjustments`, {
      method: "POST",
      body: { delta, reason },
    }),
};
