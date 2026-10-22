package at.capadocapi.service;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import at.capadocapi.service.Interfaces.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMessagePublisher messagePublisher;

    @Override
    public List<DocumentEntity> getAllDocuments() {
        return documentRepository.findAll();
    }

    @Override
    public List<DocumentEntity> getDocumentsByOwner(String ownerSub) {
        return documentRepository.findByOwnerSub(ownerSub);
    }

    @Override
    public Optional<DocumentEntity> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    @Override
    public DocumentEntity createDocument(DocumentEntity documentEntity) {
        if (documentEntity == null) {
            throw new IllegalArgumentException("DocumentEntity must not be null");
        }
        documentEntity.setUploadedAt(LocalDateTime.now());
        DocumentEntity savedEntity = documentRepository.save(documentEntity);
        log.info("Document saved to database with id={}", savedEntity.getId());

        OcrMessage ocrMessage = new OcrMessage(
                savedEntity.getId(),
                savedEntity.getFilename(),
                savedEntity.getContentType(),
                savedEntity.getUploadedAt()
        );
        messagePublisher.publishOcrMessage(ocrMessage);
        return savedEntity;
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