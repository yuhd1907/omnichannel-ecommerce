package ra.edu.identityservice.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ra.edu.common.response.ApiError;
import ra.edu.common.response.ErrorCode;
import ra.edu.common.response.FieldIssue;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Xu ly business exception (409 Conflict, 401 Unauthorized, 404 Not Found,...).
     * Bao gom ca EmailAlreadyExistsException (409), InvalidCredentialsException (401),
     * InvalidRefreshTokenException (401), ResourceNotFoundException (404).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: [{}] {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    /**
     * Xu ly loi validation du lieu tu @Valid @RequestBody (400 VALIDATION_FAILED kem danh sach errors).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<FieldIssue> fieldIssues = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldIssues.add(new FieldIssue(error.getField(), error.getDefaultMessage()));
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            fieldIssues.add(new FieldIssue(error.getObjectName(), error.getDefaultMessage()));
        }

        log.warn("Validation failed: {}", fieldIssues);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ", fieldIssues));
    }

    /**
     * Xu ly loi validate constraint tren param / query (400 VALIDATION_FAILED).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldIssue> fieldIssues = ex.getConstraintViolations().stream()
                .map(v -> new FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
                .toList();

        log.warn("Constraint violation: {}", fieldIssues);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ", fieldIssues));
    }

    /**
     * Xu ly loi JSON malformed hoac sai kieu du lieu.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "Dữ liệu JSON trong request body không hợp lệ"));
    }

    /**
     * Xu ly loi xac thuc tu Spring Security (401 INVALID_CREDENTIALS).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ErrorCode.INVALID_CREDENTIALS, "Thông tin xác thực không chính xác"));
    }

    /**
     * Xu ly loi truy cap bi tu choi tu Spring Security (403 FORBIDDEN).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", "Bạn không có quyền thực hiện hành động này"));
    }

    /**
     * Fallback xu ly tat ca cac ngoai le chua bat (500 INTERNAL_ERROR).
     * Ghi log day du stack trace vao file log server, TUYET DOI KHONG de lo stack trace
     * hay cau truc truy van SQL ra HTTP response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau"));
    }
}
