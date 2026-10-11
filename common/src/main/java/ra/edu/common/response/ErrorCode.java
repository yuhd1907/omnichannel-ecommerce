package ra.edu.common.response;

/** Ma code on dinh cho frontend - khop voi bang API contract (Muc 3). */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String SUCCESS = "SUCCESS";

    // Identity
    public static final String EMAIL_EXISTS = "EMAIL_EXISTS";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    // Product Catalog
    public static final String SKU_EXISTS = "SKU_EXISTS";

    // Order & Inventory
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String INVALID_CHANNEL = "INVALID_CHANNEL";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String SKU_UNAVAILABLE = "SKU_UNAVAILABLE";
    public static final String INVENTORY_BUSY = "INVENTORY_BUSY";
    public static final String ORDER_NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";
    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";

    // Payment
    public static final String PAYMENT_ALREADY_COMPLETED = "PAYMENT_ALREADY_COMPLETED";
    public static final String PAYMENT_NOT_READY = "PAYMENT_NOT_READY";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String PAYMENT_ACCESS_DENIED = "PAYMENT_ACCESS_DENIED";
    public static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String PAYMENT_CLOSED = "PAYMENT_CLOSED";

    // Chung
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * Code cho loi chuan cua Spring MVC (ErrorResponse) theo HTTP status, dung o fallback
     * cua GlobalExceptionHandler de 405/415/400... giu dung status thay vi bi doi thanh 500.
     */
    public static String forStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_FAILED;
            case 401 -> UNAUTHORIZED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? INTERNAL_ERROR : "HTTP_" + status;
        };
    }
}
