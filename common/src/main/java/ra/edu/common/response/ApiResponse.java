package ra.edu.common.response;

import java.time.Instant;

/**
 * Envelope chuan cho response thanh cong.
 * Theo quy uoc tai Muc 4 - tai lieu DDD/API Contract.
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS, "Success", data, null, Instant.now());
    }

    public static <T> ApiResponse<T> of(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, null, Instant.now());
    }

    public ApiResponse<T> withTraceId(String traceId) {
        return new ApiResponse<>(code, message, data, traceId, timestamp);
    }
}
