package at.capadoc.indexworker.exception;

public class IndexingPermanentException extends RuntimeException {

    public IndexingPermanentException(String message) {
        super(message);
    }

    public IndexingPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
