package ra.edu.productservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ra.edu.common.response.ApiError;
import ra.edu.common.response.ErrorCode;
import ra.edu.common.response.FieldIssue;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ApiError error = ApiError.of(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(SkuAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleSkuAlreadyExists(SkuAlreadyExistsException ex) {
        log.warn("SKU already exists: {}", ex.getMessage());
        ApiError error = ApiError.of(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.warn("Business exception: [{}] {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    /** Ảnh vượt spring.servlet.multipart.max-file-size -> 413 thay vì 500. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiError.of("INVALID_IMAGE", "Ảnh tối đa 2 MB"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<FieldIssue> fieldIssues = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldIssue(err.getField(), err.getDefaultMessage()))
                .toList();

        ApiError error = ApiError.of(ErrorCode.VALIDATION_FAILED, "Validation failed", fieldIssues);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type '%s'",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        ApiError error = ApiError.of(ErrorCode.VALIDATION_FAILED, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /** URL không khớp controller nào. Không có handler này thì catch-all bên dưới biến 404 thành 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        ApiError error = ApiError.of(ErrorCode.NOT_FOUND, "Resource not found: /" + ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /** JSON sai cú pháp / sai kiểu — không phải ErrorResponse nên phải bắt riêng, nếu không thành 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        ApiError error = ApiError.of(ErrorCode.VALIDATION_FAILED, "Malformed JSON request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        // Lỗi chuẩn của Spring MVC (405, 415, thiếu param...) đã mang sẵn HTTP status -> giữ nguyên.
        if (ex instanceof ErrorResponse er) {
            ApiError error = ApiError.of(ErrorCode.forStatus(er.getStatusCode().value()), ex.getMessage());
            return ResponseEntity.status(er.getStatusCode()).headers(er.getHeaders()).body(error);
        }
        log.error("Unhandled exception occurred", ex);
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR, "An internal server error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
