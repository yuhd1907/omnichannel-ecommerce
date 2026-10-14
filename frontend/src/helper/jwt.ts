// Chỉ ĐỌC claim để hiển thị (ẩn/hiện menu admin). Không verify chữ ký ở client:
// quyền thật do gateway + service kiểm tra trên mỗi request.

export interface JwtClaims {
  sub: string;
  email?: string;
  roles?: string[];
  exp?: number;
}

export function decodeJwt(token: string): JwtClaims | null {
  try {
    const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtClaims;
  } catch {
    return null;
  }
}

export function hasAdminRole(roles: string[] | undefined | null): boolean {
  return !!roles?.some((r) => r === "ADMIN" || r === "ROLE_ADMIN");
}
