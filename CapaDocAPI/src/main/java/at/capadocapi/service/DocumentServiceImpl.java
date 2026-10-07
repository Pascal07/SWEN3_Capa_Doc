package at.capadocapi.service;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.DocumentContentEntity;
import at.capadocapi.repository.DocumentContentRepository;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentContentRepository documentContentRepository;

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
        return documentRepository.save(documentEntity);
    }

    @Override
    @Transactional
    public DocumentEntity createDocumentWithFile(DocumentEntity documentEntity, byte[] fileContent) {
        if (documentEntity == null || fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("Document and file content must not be empty");
        }
        documentEntity.setUploadedAt(LocalDateTime.now());
        DocumentContentEntity content = new DocumentContentEntity();
        content.setDocument(documentEntity);
        content.setContent(fileContent);
        documentEntity.setFileContent(content);
        return documentRepository.save(documentEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentContentEntity> getDocumentContent(Long id) {
        return documentContentRepository.findById(id);
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