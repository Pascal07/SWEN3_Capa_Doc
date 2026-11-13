package at.capadoc.indexworker.service;

import java.time.LocalDateTime;

public interface DocumentIndexService {

    void indexDocument(Long documentId, String filename, String content, LocalDateTime uploadedAt);

    void deleteDocument(Long documentId);
}
