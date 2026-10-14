package ra.edu.identityservice.util;

/**
 * Số điện thoại di động Việt Nam: chấp nhận "0912 345 678", "0912.345.678", "+84912345678", "84912345678"
 * và lưu một dạng duy nhất "0912345678" -> so trùng (unique) đúng dù người dùng gõ khác kiểu.
 */
public final class PhoneNumbers {

    /** Dùng cho @Pattern (trước khi chuẩn hóa): đầu số 03/05/07/08/09, cho phép dấu cách, chấm, gạch. */
    public static final String VN_MOBILE_REGEX = "^(\\+?84|0)[ .-]?[35789]([ .-]?[0-9]){8}$";

    private PhoneNumbers() {
    }

    /** @return dạng 0xxxxxxxxx; null nếu đầu vào null hoặc rỗng. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("84") && digits.length() == 11) {
            return "0" + digits.substring(2);
        }
        return digits;
    }
}
