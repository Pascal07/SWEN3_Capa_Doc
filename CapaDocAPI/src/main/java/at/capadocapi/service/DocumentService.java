package at.capadocapi.service;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    public List<DocumentEntity> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Optional<DocumentEntity> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public DocumentEntity createDocument(DocumentEntity documentEntity) {
        documentEntity.setUploadedAt(LocalDateTime.now());
        return documentRepository.save(documentEntity);
    }

    public DocumentEntity updateDocument(Long id, DocumentEntity updatedDocumentEntity) {
        return documentRepository.findById(id)
                .map(existingDocumentEntity -> {
                    existingDocumentEntity.setFilename(updatedDocumentEntity.getFilename());
                    existingDocumentEntity.setContentType(updatedDocumentEntity.getContentType());
                    existingDocumentEntity.setSizeBytes(updatedDocumentEntity.getSizeBytes());
                    return documentRepository.save(existingDocumentEntity);
                })
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));
    }

    public void deleteDocument(Long id) {
        documentRepository.deleteById(id);
    }
}
