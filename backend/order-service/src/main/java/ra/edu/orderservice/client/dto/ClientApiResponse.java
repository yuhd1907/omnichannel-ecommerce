package ra.edu.orderservice.client.dto;

/**
 * Wrapper khớp với ApiResponse record của common module.
 * Feign cần class cụ thể (không dùng được record generic của common
 * vì Jackson cần constructor rõ khi deserialize generic type).
 */
public record ClientApiResponse<T>(
        String code,
        String message,
        T data
) {}
