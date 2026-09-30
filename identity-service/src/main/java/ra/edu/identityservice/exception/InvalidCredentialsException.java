package ra.edu.identityservice.exception;

import org.springframework.http.HttpStatus;
import ra.edu.common.response.ErrorCode;

public class InvalidCredentialsException extends AppException {

    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message, HttpStatus.UNAUTHORIZED);
    }
}
