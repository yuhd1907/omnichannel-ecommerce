// localStorage có thể ném lỗi (chế độ riêng tư, bị chặn) và không tồn tại khi render trên server.

export function readJson<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function readString(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function write(key: string, value: string | object | null): void {
  if (typeof window === "undefined") return;
  try {
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, typeof value === "string" ? value : JSON.stringify(value));
  } catch {
    // bỏ qua: mất giỏ hàng/đăng nhập khi tải lại trang, không ảnh hưởng thao tác hiện tại
  }
}
