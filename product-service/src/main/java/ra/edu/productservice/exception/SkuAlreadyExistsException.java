package ra.edu.productservice.exception;

import org.springframework.http.HttpStatus;

public class SkuAlreadyExistsException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public SkuAlreadyExistsException(String message) {
        super(message);
        this.code = "SKU_EXISTS";
        this.status = HttpStatus.CONFLICT;
    }

    public SkuAlreadyExistsException(String code, String message) {
        super(message);
        this.code = code;
        this.status = HttpStatus.CONFLICT;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
