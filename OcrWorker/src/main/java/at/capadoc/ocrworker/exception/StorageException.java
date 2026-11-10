package at.capadoc.ocrworker.exception;

public class StorageException extends RuntimeException {

    private final boolean notFound;

    public StorageException(String message) {
        super(message);
        this.notFound = false;
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
        this.notFound = false;
    }

    public StorageException(String message, Throwable cause, boolean notFound) {
        super(message, cause);
        this.notFound = notFound;
    }

    public boolean isNotFound() {
        return notFound;
    }
}
