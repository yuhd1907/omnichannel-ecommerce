package ra.edu.common.response;

import java.util.List;

/** Dinh dang phan trang dung chung: data: {items, page, size, totalElements, totalPages}. */
public record PageData<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
