package ra.edu.identityservice.exception;

import org.springframework.http.HttpStatus;
import ra.edu.common.response.ErrorCode;

public class EmailAlreadyExistsException extends AppException {

    public EmailAlreadyExistsException(String message) {
        super(ErrorCode.EMAIL_EXISTS, message, HttpStatus.CONFLICT);
    }
}
