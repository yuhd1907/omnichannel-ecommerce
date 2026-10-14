package ra.edu.identityservice.exception;

import org.springframework.http.HttpStatus;
import ra.edu.common.response.ErrorCode;

public class PhoneAlreadyExistsException extends AppException {

    public PhoneAlreadyExistsException(String message) {
        super(ErrorCode.PHONE_EXISTS, message, HttpStatus.CONFLICT);
    }
}
