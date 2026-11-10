package at.capadoc.ocrworker.exception;

public class OcrPermanentException extends OcrException {

    public OcrPermanentException(String message) {
        super(message);
    }

    public OcrPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
