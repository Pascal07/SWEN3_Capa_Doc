package at.capadocapi.exception;

import org.springframework.dao.EmptyResultDataAccessException;

public class DataAccessLayerException extends EmptyResultDataAccessException {
    private final String message;

    public DataAccessLayerException(String message, Throwable cause) {
        super(1);
        this.message = message;
        initCause(cause);
    }

    @Override
    public String getMessage() {
        return message;
    }
}
