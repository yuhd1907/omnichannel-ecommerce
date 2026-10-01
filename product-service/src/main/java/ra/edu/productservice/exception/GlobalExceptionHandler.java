package ra.edu.productservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        log.error("Unhandled exception occurred", ex);
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR, "An internal server error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
