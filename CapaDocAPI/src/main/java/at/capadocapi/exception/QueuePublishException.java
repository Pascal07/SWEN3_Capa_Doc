package at.capadocapi.exception;

public class QueuePublishException extends RuntimeException {
    public QueuePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}