import { z } from "zod";

/** Khớp PhoneNumbers.VN_MOBILE_REGEX của identity-service: di động VN, cho phép dấu cách/chấm/gạch. */
const VN_MOBILE = /^(\+?84|0)[ .-]?[35789]([ .-]?[0-9]){8}$/;
const VN_MOBILE_MESSAGE = "Số di động Việt Nam không hợp lệ (vd 0912 345 678)";

// Khớp ràng buộc @Valid của identity-service (LoginRequest, RegisterRequest, AddressRequest).

export const loginSchema = z.object({
  email: z.email("Email không hợp lệ"),
  password: z.string().min(1, "Vui lòng nhập mật khẩu"),
});

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, "Họ tên tối thiểu 2 ký tự").max(255, "Tối đa 255 ký tự"),
    email: z.email("Email không hợp lệ"),
    // Tùy chọn; mỗi số chỉ đăng ký được cho 1 tài khoản (server trả PHONE_EXISTS)
    phone: z.string().trim().refine((v) => v === "" || VN_MOBILE.test(v), VN_MOBILE_MESSAGE),
    password: z.string().min(6, "Mật khẩu tối thiểu 6 ký tự").max(72, "Mật khẩu tối đa 72 ký tự"),
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu nhập lại không khớp",
  });

export const addressSchema = z.object({
  recipientName: z.string().trim().min(1, "Vui lòng nhập tên người nhận").max(255),
  phone: z.string().trim().regex(VN_MOBILE, VN_MOBILE_MESSAGE),
  addressLine: z.string().trim().min(1, "Vui lòng nhập địa chỉ").max(255),
  ward: z.string().trim().min(1, "Vui lòng nhập phường/xã").max(100),
  district: z.string().trim().min(1, "Vui lòng nhập quận/huyện").max(100),
  city: z.string().trim().min(1, "Vui lòng nhập tỉnh/thành phố").max(100),
  isDefault: z.boolean(),
});

export type LoginInput = z.infer<typeof loginSchema>;
export type RegisterInput = z.infer<typeof registerSchema>;
export type AddressInput = z.infer<typeof addressSchema>;
