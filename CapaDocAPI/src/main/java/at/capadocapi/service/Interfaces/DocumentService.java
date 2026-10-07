package at.capadocapi.service.Interfaces;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.DocumentContentEntity;
import java.util.List;
import java.util.Optional;

public interface DocumentService {
    List<DocumentEntity> getAllDocuments();
    List<DocumentEntity> getDocumentsByOwner(String ownerSub);
    Optional<DocumentEntity> getDocumentById(Long id);
    DocumentEntity createDocument(DocumentEntity documentEntity);
    DocumentEntity createDocumentWithFile(DocumentEntity documentEntity, byte[] fileContent);
    Optional<DocumentContentEntity> getDocumentContent(Long id);
    DocumentEntity updateDocument(Long id, DocumentEntity updatedDocumentEntity);
    void deleteDocument(Long id);
}