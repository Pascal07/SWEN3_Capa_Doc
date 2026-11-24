package capa.genaiworker.exception;

public class OllamaResponseException extends GenAiException {

    public OllamaResponseException(String message) {
        super(message);
    }

    public OllamaResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
