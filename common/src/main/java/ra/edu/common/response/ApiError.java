package ra.edu.common.response;

import java.time.Instant;
import java.util.List;

/**
 * Envelope chuan cho response loi. Luon di kem HTTP status that
 * (400/401/403/404/409/422/429/500/503) - khong tra 200 khi co loi.
 */
public record ApiError(
        String code,
        String message,
        Object data,
        List<FieldIssue> errors,
        String traceId,
        Instant timestamp
) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, null, null, Instant.now());
    }

    public static ApiError of(String code, String message, List<FieldIssue> errors) {
        return new ApiError(code, message, null, errors, null, Instant.now());
    }

    public ApiError withTraceId(String traceId) {
        return new ApiError(code, message, data, errors, traceId, timestamp);
    }
}
