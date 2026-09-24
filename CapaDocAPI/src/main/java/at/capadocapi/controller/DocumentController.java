package at.capadocapi.controller;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.DocumentRequestDTO;
import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.service.Interfaces.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<DocumentResponseDTO>> getAllDocuments() {
        List<DocumentResponseDTO> response = documentService.getAllDocuments()
                .stream()
                .map(DocumentResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(id)
                .map(DocumentResponseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DocumentResponseDTO> createDocument(@Valid @RequestBody DocumentRequestDTO request) {
        DocumentEntity toCreate = DocumentEntity.builder()
                .filename(request.getFilename())
                .contentType(request.getContentType())
                .sizeBytes(request.getSizeBytes())
                .build();

        DocumentEntity created = documentService.createDocument(toCreate);
        return new ResponseEntity<>(DocumentResponseDTO.from(created), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequestDTO request) {
        try {
            DocumentEntity toUpdate = DocumentEntity.builder()
                    .filename(request.getFilename())
                    .contentType(request.getContentType())
                    .sizeBytes(request.getSizeBytes())
                    .build();

            DocumentEntity updated = documentService.updateDocument(id, toUpdate);
            return ResponseEntity.ok(DocumentResponseDTO.from(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}