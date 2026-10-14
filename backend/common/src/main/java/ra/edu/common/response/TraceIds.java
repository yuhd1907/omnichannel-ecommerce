package ra.edu.common.response;

import org.slf4j.MDC;

/**
 * traceId cua request hien tai. Micrometer Tracing ghi traceId vao MDC cho moi request/message
 * (cung gia tri in trong log va hien trong Jaeger) -> client bao loi kem traceId la tim duoc ngay
 * log (Loki) va trace (Jaeger) cua dung request do. Ngoai request (test, job) tra null.
 */
final class TraceIds {

    private TraceIds() {
    }

    static String current() {
        String id = MDC.get("traceId");
        return id == null || id.isBlank() ? null : id;
    }
}
