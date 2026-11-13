package at.capadocapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class StorageObjectNotFoundException extends StorageException {

    public StorageObjectNotFoundException(String key) {
        super("Storage object not found for key: " + key, null, true);
    }

    public StorageObjectNotFoundException(String key, Throwable cause) {
        super("Storage object not found for key: " + key, cause, true);
    }
}
