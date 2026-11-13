package at.capadoc.ocrworker.exception;

public class StorageObjectNotFoundException extends StorageException {

    public StorageObjectNotFoundException(String key) {
        super("Storage object not found for key: " + key, null, true);
    }

    public StorageObjectNotFoundException(String key, Throwable cause) {
        super("Storage object not found for key: " + key, cause, true);
    }
}
