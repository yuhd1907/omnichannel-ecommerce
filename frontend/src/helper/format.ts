const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat("vi-VN", {
  hour: "2-digit",
  minute: "2-digit",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

export function formatCurrency(amount: number | null | undefined): string {
  return currency.format(Number(amount ?? 0));
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : dateTime.format(d);
}

/** 8 ký tự đầu của UUID, đủ để người dùng nhận ra đơn. */
export function shortId(id: string): string {
  return id.slice(0, 8).toUpperCase();
}

/** "Màu: Đen · Dung lượng: 256GB" từ attributes của SKU; không có thì dùng skuCode. */
export function skuLabel(attributes: Record<string, unknown> | null | undefined, skuCode: string): string {
  const values = Object.values(attributes ?? {}).filter((v) => v !== null && v !== "");
  return values.length ? values.map(String).join(" · ") : skuCode;
}

/** crypto.randomUUID chỉ có trong secure context (HTTPS/localhost); truy cập qua IP http thì tự sinh UUID v4. */
export function newId(): string {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}
