package at.capadoc.ocrworker.service;

public interface StorageService {

    String DOCUMENT_KEY_PREFIX = "documents/";
    String DOCUMENT_KEY_SUFFIX = ".pdf";

    void upload(String key, byte[] content, String contentType);

    byte[] download(String key);

    void delete(String key);

    default String buildDocumentKey(Long documentId) {
        return DOCUMENT_KEY_PREFIX + documentId + DOCUMENT_KEY_SUFFIX;
    }
}
