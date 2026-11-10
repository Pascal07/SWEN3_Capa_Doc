package at.capadoc.ocrworker.exception;

public class OcrTransientException extends OcrException {

    public OcrTransientException(String message) {
        super(message);
    }

    public OcrTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
