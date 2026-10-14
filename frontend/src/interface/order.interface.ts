// order-service: OrderDto, OrderSummaryDto, CreateOrderRequest, InventoryDto

export type OrderStatus = "PENDING" | "CONFIRMED" | "SHIPPING" | "COMPLETED" | "CANCELLED";
export type PaymentStatus = "UNPAID" | "PAID" | "FAILED" | "REFUNDED";

export interface OrderItem {
  id: string;
  skuCode: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface Order {
  id: string;
  userId: string;
  channel: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  /** COD | VNPAY | MOCK; null khi đơn chưa chọn cách thanh toán */
  paymentMethod: string | null;
  totalAmount: number;
  shippingRecipientName: string;
  shippingPhone: string;
  shippingAddress: string;
  createdAt: string;
  items: OrderItem[];
}

export interface OrderSummary {
  id: string;
  channel: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  paymentMethod: string | null;
  totalAmount: number;
  createdAt: string;
}

export interface CreateOrderRequest {
  channel: "WEB" | "MOBILE";
  addressId: string;
  items: { skuCode: string; quantity: number }[];
}

export interface Inventory {
  id: string;
  skuCode: string;
  availableQty: number;
  reservedQty: number;
  version: number;
}
