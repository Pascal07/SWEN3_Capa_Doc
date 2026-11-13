package at.capadocapi.service.Interfaces;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

    String DOCUMENT_KEY_PREFIX = "documents/";
    String DOCUMENT_KEY_SUFFIX = ".pdf";

    void upload(String key, byte[] content, String contentType);

    default void upload(String key, InputStream inputStream, long contentLength, String contentType) {
        try {
            upload(key, inputStream.readAllBytes(), contentType);
        } catch (IOException ex) {
            throw new at.capadocapi.exception.StorageException("Failed to read input stream for key: " + key, ex);
        }
    }

    byte[] download(String key);

    void delete(String key);

    default String buildDocumentKey(Long documentId) {
        return DOCUMENT_KEY_PREFIX + documentId + DOCUMENT_KEY_SUFFIX;
    }
}
