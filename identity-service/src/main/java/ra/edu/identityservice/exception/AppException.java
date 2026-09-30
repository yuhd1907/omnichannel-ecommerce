package ra.edu.identityservice.exception;

import org.springframework.http.HttpStatus;

public class AppException extends BusinessException {

    public AppException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public AppException(String code, String message) {
        super(code, message);
    }
}
