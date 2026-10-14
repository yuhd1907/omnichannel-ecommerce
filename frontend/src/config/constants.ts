import type { OrderStatus, PaymentStatus } from "@/interface/order.interface";

/** Cùng origin -> next.config.ts rewrite sang API Gateway. */
export const API_BASE = "/api/v1";

/** Khớp app.inventory.reservation-ttl-minutes của order-service (OrderExpiryJob). */
export const RESERVATION_TTL_MINUTES = 15;

export const SHOP_NAME = "gom";
export const SHOP_TAGLINE = "Gom mọi kênh, một giỏ hàng";

export const STORAGE_KEYS = {
  accessToken: "omni.accessToken",
  refreshToken: "omni.refreshToken",
  user: "omni.user",
  cart: "omni.cart",
} as const;

type Tone = "amber" | "emerald" | "sky" | "violet" | "rose" | "slate";

export const ORDER_STATUS: Record<OrderStatus, { label: string; tone: Tone; desc: string }> = {
  PENDING: { label: "Chờ thanh toán", tone: "amber", desc: `Đang giữ hàng, tự hủy sau ${RESERVATION_TTL_MINUTES} phút nếu chưa thanh toán` },
  CONFIRMED: { label: "Đã xác nhận", tone: "emerald", desc: "Thanh toán thành công, kho đã trừ" },
  SHIPPING: { label: "Đang giao", tone: "sky", desc: "Đơn hàng đang trên đường giao" },
  COMPLETED: { label: "Hoàn thành", tone: "violet", desc: "Khách đã nhận hàng" },
  CANCELLED: { label: "Đã hủy", tone: "rose", desc: "Đơn bị hủy, hàng giữ đã trả lại kho" },
};

export const PAYMENT_STATUS: Record<PaymentStatus, { label: string; tone: Tone }> = {
  UNPAID: { label: "Chưa thanh toán", tone: "amber" },
  PAID: { label: "Đã thanh toán", tone: "emerald" },
  FAILED: { label: "Thanh toán lỗi", tone: "rose" },
  REFUNDED: { label: "Đã hoàn tiền", tone: "violet" },
};

export const PAYMENT_METHOD_LABEL: Record<string, string> = {
  COD: "Thanh toán khi nhận hàng",
  VNPAY: "VNPAY",
  MOCK: "Cổng thử nghiệm",
};

export const TONE_CLASS: Record<Tone, string> = {
  amber: "bg-amber-50 text-amber-700 ring-amber-200",
  emerald: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  sky: "bg-sky-50 text-sky-700 ring-sky-200",
  violet: "bg-violet-50 text-violet-700 ring-violet-200",
  rose: "bg-rose-50 text-rose-700 ring-rose-200",
  slate: "bg-neutral-100 text-neutral-700 ring-neutral-200",
};

/** Thông báo tiếng Việt cho ErrorCode của backend (common/response/ErrorCode.java). */
export const ERROR_MESSAGES: Record<string, string> = {
  EMAIL_EXISTS: "Email đã được đăng ký",
  PHONE_EXISTS: "Số điện thoại đã được đăng ký cho tài khoản khác",
  PAYMENT_METHOD_LOCKED: "Đơn đã chọn thanh toán khi nhận hàng, không đổi sang trả trước được",
  INVALID_CREDENTIALS: "Email hoặc mật khẩu không đúng",
  INVALID_REFRESH_TOKEN: "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại",
  OUT_OF_STOCK: "Sản phẩm không đủ hàng trong kho",
  SKU_UNAVAILABLE: "Sản phẩm không còn bán",
  INVENTORY_BUSY: "Kho đang bận xử lý, vui lòng thử lại sau giây lát",
  ORDER_NOT_CANCELLABLE: "Đơn hàng không còn ở trạng thái có thể hủy",
  INVALID_STATUS_TRANSITION: "Không thể chuyển sang trạng thái này",
  IDEMPOTENCY_KEY_REUSED: "Yêu cầu đặt hàng bị trùng với nội dung khác, vui lòng thử lại",
  PAYMENT_ALREADY_COMPLETED: "Đơn hàng đã được thanh toán",
  PAYMENT_NOT_READY: "Hệ thống đang khởi tạo thanh toán, vui lòng thử lại",
  PAYMENT_NOT_FOUND: "Chưa có thông tin thanh toán cho đơn này",
  PAYMENT_ACCESS_DENIED: "Bạn không phải chủ đơn hàng này",
  PAYMENT_CLOSED: "Đơn đã hủy hoặc hết hạn, không thể thanh toán",
  PAYMENT_GATEWAY_NOT_CONFIGURED: "Cổng thanh toán này chưa được cấu hình trên server",
  AMOUNT_MISMATCH: "Số tiền thanh toán không khớp",
  INVALID_SIGNATURE: "Chữ ký webhook không hợp lệ",
  UNAUTHORIZED: "Vui lòng đăng nhập",
  ACCESS_DENIED: "Bạn không có quyền thực hiện thao tác này",
  NOT_FOUND: "Không tìm thấy dữ liệu",
  VALIDATION_FAILED: "Dữ liệu không hợp lệ",
  RATE_LIMITED: "Bạn thao tác quá nhanh, vui lòng thử lại sau",
  INTERNAL_ERROR: "Lỗi hệ thống, vui lòng thử lại",
};
