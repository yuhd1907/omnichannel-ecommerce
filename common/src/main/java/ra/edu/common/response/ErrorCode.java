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
    public static final String ORDER_NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";
    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";

    // Payment
    public static final String PAYMENT_ALREADY_COMPLETED = "PAYMENT_ALREADY_COMPLETED";

    // Chung
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
