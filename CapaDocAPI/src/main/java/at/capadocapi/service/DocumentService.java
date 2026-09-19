package at.capadocapi.service;

import at.capadocapi.model.Document;
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

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public Document createDocument(Document document) {
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    public Document updateDocument(Long id, Document updatedDocument) {
        return documentRepository.findById(id)
                .map(existingDocument -> {
                    existingDocument.setTitle(updatedDocument.getTitle());
                    existingDocument.setDescription(updatedDocument.getDescription());
                    existingDocument.setContent(updatedDocument.getContent());
                    existingDocument.setUpdatedAt(LocalDateTime.now());
                    return documentRepository.save(existingDocument);
                })
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));
    }

    public void deleteDocument(Long id) {
        documentRepository.deleteById(id);
    }
}
