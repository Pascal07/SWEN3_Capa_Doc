package at.capadocapi.service;

import at.capadocapi.exception.DataAccessLayerException;
import at.capadocapi.exception.DocumentNotFoundException;
import at.capadocapi.exception.StorageObjectNotFoundException;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import at.capadocapi.service.Interfaces.DocumentService;
import at.capadocapi.service.Interfaces.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    public static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final byte[] PDF_MAGIC_BYTES = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final DocumentRepository documentRepository;
    private final DocumentMessagePublisher messagePublisher;
    private final StorageService storageService;

    @Autowired
    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentMessagePublisher messagePublisher,
            StorageService storageService) {
        this.documentRepository = documentRepository;
        this.messagePublisher = messagePublisher;
        this.storageService = storageService;
    }

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentMessagePublisher messagePublisher) {
        this(documentRepository, messagePublisher, null);
    }

    public static byte[] validateAndReadPdfFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Only PDF files (Content-Type application/pdf) are allowed");
        }
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read uploaded file", ex);
        }
        if (bytes.length < PDF_MAGIC_BYTES.length) {
            throw new IllegalArgumentException("Invalid PDF file: missing %PDF magic bytes");
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (bytes[i] != PDF_MAGIC_BYTES[i]) {
                throw new IllegalArgumentException("Invalid PDF file: missing %PDF magic bytes");
            }
        }
        return bytes;
    }

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
        if (documentEntity.getOcrStatus() == null) {
            documentEntity.setOcrStatus(ProcessingStatus.PENDING);
        }
        if (documentEntity.getIndexStatus() == null) {
            documentEntity.setIndexStatus(ProcessingStatus.PENDING);
        }

        final DocumentEntity savedEntity;
        try {
            savedEntity = documentRepository.save(documentEntity);
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not save document", ex);
        }
        log.info("Document saved to database with id={}", savedEntity.getId());

        OcrMessage ocrMessage = new OcrMessage(
                savedEntity.getId(),
                savedEntity.getStorageKey(),
                savedEntity.getFilename(),
                savedEntity.getContentType(),
                savedEntity.getUploadedAt()
        );
        publishOcrMessageAfterCommit(ocrMessage);
        return savedEntity;
    }

    @Override
    @Transactional
    public DocumentEntity createDocument(DocumentEntity documentEntity, MultipartFile file) {
        if (documentEntity == null) {
            throw new IllegalArgumentException("DocumentEntity must not be null");
        }
        byte[] pdfBytes = validateAndReadPdfFile(file);

        if (documentEntity.getFilename() == null || documentEntity.getFilename().isBlank()) {
            String originalFilename = file.getOriginalFilename();
            documentEntity.setFilename(
                    (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "document.pdf"
            );
        }
        documentEntity.setContentType(PDF_CONTENT_TYPE);
        documentEntity.setSizeBytes((long) pdfBytes.length);
        documentEntity.setUploadedAt(LocalDateTime.now());
        if (documentEntity.getOcrStatus() == null) {
            documentEntity.setOcrStatus(ProcessingStatus.PENDING);
        }
        if (documentEntity.getIndexStatus() == null) {
            documentEntity.setIndexStatus(ProcessingStatus.PENDING);
        }

        final DocumentEntity createdEntity;
        try {
            createdEntity = documentRepository.save(documentEntity);
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not save document", ex);
        }

        String storageKey = StorageService.DOCUMENT_KEY_PREFIX
                + createdEntity.getId()
                + StorageService.DOCUMENT_KEY_SUFFIX;

        if (storageService != null) {
            storageService.upload(storageKey, pdfBytes, PDF_CONTENT_TYPE);
        }

        createdEntity.setStorageKey(storageKey);
        final DocumentEntity finalSavedEntity;
        try {
            finalSavedEntity = documentRepository.save(createdEntity);
        } catch (DataAccessException ex) {
            if (storageService != null) {
                try {
                    storageService.delete(storageKey);
                } catch (Exception cleanupEx) {
                    log.error("Failed to clean up RustFS object '{}' after DB save error", storageKey, cleanupEx);
                }
            }
            throw dataAccessException("Could not update storageKey for document " + createdEntity.getId(), ex);
        }

        log.info("Document saved with id={} and uploaded to RustFS with storageKey={}",
                finalSavedEntity.getId(), finalSavedEntity.getStorageKey());

        OcrMessage ocrMessage = new OcrMessage(
                finalSavedEntity.getId(),
                finalSavedEntity.getStorageKey(),
                finalSavedEntity.getFilename(),
                finalSavedEntity.getContentType(),
                finalSavedEntity.getUploadedAt()
        );
        publishOcrMessageAfterCommit(ocrMessage);
        return finalSavedEntity;
    }

    @Override
    public byte[] downloadDocument(Long id) {
        final DocumentEntity document;
        try {
            document = documentRepository.findById(id)
                    .orElseThrow(() -> new DocumentNotFoundException(id));
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not load document " + id, ex);
        }

        String storageKey = (document.getStorageKey() != null && !document.getStorageKey().isBlank())
                ? document.getStorageKey()
                : StorageService.DOCUMENT_KEY_PREFIX + id + StorageService.DOCUMENT_KEY_SUFFIX;

        if (storageService == null) {
            throw new StorageObjectNotFoundException(storageKey);
        }
        return storageService.download(storageKey);
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
    @Transactional
    public void deleteDocument(Long id) {
        try {
            Optional<DocumentEntity> existingOpt = documentRepository.findById(id);
            String storageKey = existingOpt
                    .map(DocumentEntity::getStorageKey)
                    .filter(key -> key != null && !key.isBlank())
                    .orElse(StorageService.DOCUMENT_KEY_PREFIX + id + StorageService.DOCUMENT_KEY_SUFFIX);

            if (storageService != null) {
                try {
                    storageService.delete(storageKey);
                } catch (StorageObjectNotFoundException ex) {
                    log.warn("Object '{}' for document id={} not found in RustFS during delete; continuing DB deletion.",
                            storageKey, id);
                }
            }

            if (existingOpt.isPresent() && existingOpt.get().getShareLinks() != null
                    && !existingOpt.get().getShareLinks().isEmpty()) {
                DocumentEntity existing = existingOpt.get();
                existing.getShareLinks().clear();
                documentRepository.save(existing);
            }

            documentRepository.deleteById(id);
        } catch (DataAccessException ex) {
            throw dataAccessException("Could not delete document " + id, ex);
        }
    }

    private void publishOcrMessageAfterCommit(OcrMessage ocrMessage) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagePublisher.publishOcrMessage(ocrMessage);
                }
            });
        } else {
            messagePublisher.publishOcrMessage(ocrMessage);
        }
    }

    private DataAccessLayerException dataAccessException(String message, DataAccessException cause) {
        log.error(message, cause);
        return new DataAccessLayerException(message, cause);
    }
}
