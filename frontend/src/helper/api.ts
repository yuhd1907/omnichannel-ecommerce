import { API_BASE, ERROR_MESSAGES, STORAGE_KEYS } from "@/config/constants";
import type { ApiErrorBody, ApiResponse, FieldIssue } from "@/interface/api.interface";
import type { AuthTokens } from "@/interface/user.interface";
import { readString, write } from "@/helper/storage";

/** Phát ra khi refresh token hết hạn -> AuthProvider đăng xuất và báo người dùng. */
export const SESSION_EXPIRED_EVENT = "omni:session-expired";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldIssue[] = [],
    readonly traceId: string | null = null,
  ) {
    super(message);
  }
}

/** Câu thông báo cho người dùng: ưu tiên bản dịch theo code, rồi message của server. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.fieldErrors.length) return err.fieldErrors.map((f) => `${f.field}: ${f.message}`).join("; ");
    return ERROR_MESSAGES[err.code] ?? err.message;
  }
  if (err instanceof TypeError) return "Không kết nối được tới máy chủ";
  return err instanceof Error ? err.message : "Có lỗi xảy ra";
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  /** false: không gắn Authorization (login/register/refresh). */
  auth?: boolean;
}

export interface ApiResult<T> {
  data: T;
  status: number;
  headers: Headers;
}

// Nhiều request cùng nhận 401 -> chỉ refresh MỘT lần, các request còn lại chờ chung promise.
let refreshing: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = readString(STORAGE_KEYS.refreshToken);
  if (!refreshToken) return false;
  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const body = (await res.json()) as ApiResponse<AuthTokens>;
    write(STORAGE_KEYS.accessToken, body.data.accessToken);
    if (body.data.refreshToken) write(STORAGE_KEYS.refreshToken, body.data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

async function send(path: string, opts: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json", ...opts.headers };
  const isForm = typeof FormData !== "undefined" && opts.body instanceof FormData;
  if (opts.body !== undefined && !isForm) headers["Content-Type"] = "application/json";
  const token = opts.auth === false ? null : readString(STORAGE_KEYS.accessToken);
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(`${API_BASE}${path}`, {
    method: opts.method ?? "GET",
    headers,
    body: opts.body === undefined ? undefined : isForm ? (opts.body as FormData) : JSON.stringify(opts.body),
    cache: "no-store",
  });
}

async function toError(res: Response): Promise<ApiError> {
  const text = await res.text();
  try {
    const body = JSON.parse(text) as Partial<ApiErrorBody>;
    return new ApiError(res.status, body.code ?? `HTTP_${res.status}`, body.message ?? text, body.errors ?? [], body.traceId ?? null);
  } catch {
    const code = res.status === 429 ? "RATE_LIMITED" : res.status >= 500 ? "INTERNAL_ERROR" : `HTTP_${res.status}`;
    return new ApiError(res.status, code, text || `Lỗi máy chủ (${res.status})`);
  }
}

/** Gọi API Gateway, tự bóc envelope ApiResponse và tự refresh access token (15 phút) một lần khi 401. */
export async function apiRequest<T>(path: string, opts: RequestOptions = {}): Promise<ApiResult<T>> {
  let res = await send(path, opts);

  if (res.status === 401 && opts.auth !== false) {
    // Có refresh token -> làm mới access token (15 phút) một lần rồi gửi lại
    if (readString(STORAGE_KEYS.refreshToken)) {
      refreshing ??= refreshAccessToken().finally(() => {
        refreshing = null;
      });
      if (await refreshing) res = await send(path, opts);
    }
    // Vẫn 401 (token hết hạn, bị thu hồi, hoặc mất token nhưng còn thông tin user) -> đăng xuất + mở đăng nhập,
    // tránh trạng thái header vẫn hiện tên mà mọi request đều 401
    if (res.status === 401 && readString(STORAGE_KEYS.user)) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
  }

  if (!res.ok) throw await toError(res);
  if (res.status === 204) return { data: undefined as T, status: res.status, headers: res.headers };
  const body = (await res.json()) as ApiResponse<T>;
  return { data: body.data, status: res.status, headers: res.headers };
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  return (await apiRequest<T>(path, opts)).data;
}
