package at.capadocapi.exception;

public class SearchServiceUnavailableException extends RuntimeException {

    public SearchServiceUnavailableException(String message) {
        super(message);
    }

    public SearchServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
