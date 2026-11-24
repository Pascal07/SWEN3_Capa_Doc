package capa.genaiworker.exception;

public class OllamaUnavailableException extends GenAiException {

    public OllamaUnavailableException(String message) {
        super(message);
    }

    public OllamaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
