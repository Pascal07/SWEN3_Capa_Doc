package capa.genaiworker.exception;

public abstract class GenAiException extends RuntimeException {

    protected GenAiException(String message) {
        super(message);
    }

    protected GenAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
