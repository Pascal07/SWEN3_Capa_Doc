package at.capadoc.indexworker.exception;

public class IndexingTransientException extends RuntimeException {

    public IndexingTransientException(String message) {
        super(message);
    }

    public IndexingTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
