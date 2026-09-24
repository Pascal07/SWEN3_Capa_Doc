package at.capadocapi.service;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;

    @Override
    public List<DocumentEntity> getAllDocuments() {
        return documentRepository.findAll();
    }

    @Override
    public Optional<DocumentEntity> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    @Override
    public DocumentEntity createDocument(DocumentEntity documentEntity) {
        documentEntity.setUploadedAt(LocalDateTime.now());
        return documentRepository.save(documentEntity);
    }

    @Override
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

    @Override
    public void deleteDocument(Long id) {
        documentRepository.deleteById(id);
    }
}