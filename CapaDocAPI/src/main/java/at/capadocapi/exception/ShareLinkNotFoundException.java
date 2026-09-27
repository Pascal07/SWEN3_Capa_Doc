package at.capadocapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ShareLinkNotFoundException extends RuntimeException {

    public ShareLinkNotFoundException(String shortCode) {
        super("Share link not found: " + shortCode);
    }

    public ShareLinkNotFoundException() {
        super("Share link not found");
    }
}
