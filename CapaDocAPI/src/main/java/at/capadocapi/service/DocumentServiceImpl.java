package at.capadocapi.service;

import at.capadocapi.exception.DataAccessLayerException;
import at.capadocapi.exception.DocumentNotFoundException;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;

    @Override
    public List<DocumentEntity> getAllDocuments() {
        try {
            return documentRepository.findAll();
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not load documents", ex);
        }
    }

    @Override
    public List<DocumentEntity> getDocumentsByOwner(String ownerSub) {
        try {
            return documentRepository.findByOwnerSub(ownerSub);
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not load documents for owner " + ownerSub, ex);
        }
    }

    @Override
    public Optional<DocumentEntity> getDocumentById(Long id) {
        try {
            return documentRepository.findById(id);
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not load document " + id, ex);
        }
    }

    @Override
    @Transactional
    public DocumentEntity createDocument(DocumentEntity documentEntity) {
        if (documentEntity == null) {
            throw new IllegalArgumentException("DocumentEntity must not be null");
        }
        documentEntity.setUploadedAt(LocalDateTime.now());
        final DocumentEntity savedEntity;
        try {
            savedEntity = documentRepository.save(documentEntity);
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not save document", ex);
        }
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
        try {
            return documentRepository.findById(id)
                    .map(existingDocumentEntity -> {
                        existingDocumentEntity.setFilename(updatedDocumentEntity.getFilename());
                        existingDocumentEntity.setContentType(updatedDocumentEntity.getContentType());
                        existingDocumentEntity.setSizeBytes(updatedDocumentEntity.getSizeBytes());
                        return documentRepository.save(existingDocumentEntity);
                    })
                    .orElseThrow(() -> new DocumentNotFoundException(id));
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not update document " + id, ex);
        }
    }

    @Override
    public void deleteDocument(Long id) {
        try {
            documentRepository.deleteById(id);
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not delete document " + id, ex);
        }
    }

    private DataAccessLayerException dataAccessException(String message, DataAccessException cause) {
        log.error(message, cause);
        return new DataAccessLayerException(message, cause);
    }
}