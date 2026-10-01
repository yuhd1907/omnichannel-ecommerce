package ra.edu.productservice.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ResourceNotFoundException(String message) {
        super(message);
        this.code = "RESOURCE_NOT_FOUND";
        this.status = HttpStatus.NOT_FOUND;
    }

    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
        this.status = HttpStatus.NOT_FOUND;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
