package at.capadocapi.service.Interfaces;

import at.capadocapi.model.DocumentEntity;
import java.util.List;
import java.util.Optional;

public interface DocumentService {
    List<DocumentEntity> getAllDocuments();
    Optional<DocumentEntity> getDocumentById(Long id);
    DocumentEntity createDocument(DocumentEntity documentEntity);
    DocumentEntity updateDocument(Long id, DocumentEntity updatedDocumentEntity);
    void deleteDocument(Long id);
}