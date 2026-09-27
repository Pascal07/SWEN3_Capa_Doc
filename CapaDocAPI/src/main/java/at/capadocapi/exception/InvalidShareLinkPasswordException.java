package at.capadocapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidShareLinkPasswordException extends RuntimeException {

    public InvalidShareLinkPasswordException() {
        super("Invalid share link password");
    }

    public InvalidShareLinkPasswordException(String message) {
        super(message);
    }
}
