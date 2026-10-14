package ra.edu.identityservice.exception;

import org.springframework.http.HttpStatus;
import ra.edu.common.response.ErrorCode;

public class InvalidRefreshTokenException extends AppException {

    public InvalidRefreshTokenException(String message) {
        super(ErrorCode.INVALID_REFRESH_TOKEN, message, HttpStatus.UNAUTHORIZED);
    }
}
