package at.capadocapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class ShareLinkExpiredException extends RuntimeException {

    public ShareLinkExpiredException(String shortCode) {
        super("Share link has expired: " + shortCode);
    }

    public ShareLinkExpiredException() {
        super("Share link has expired");
    }
}
