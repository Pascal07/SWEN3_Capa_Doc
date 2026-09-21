package at.capadocapi.controller;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.service.DocumentService;
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
    public ResponseEntity<List<at.capadocapi.model.dto.DocumentResponseDTO>> getAllDocuments() {
        List<at.capadocapi.model.dto.DocumentResponseDTO> response = documentService.getAllDocuments()
                .stream()
                .map(at.capadocapi.model.dto.DocumentResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<at.capadocapi.model.dto.DocumentResponseDTO> getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(id)
                .map(at.capadocapi.model.dto.DocumentResponseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<at.capadocapi.model.dto.DocumentResponseDTO> createDocument(@Valid @RequestBody at.capadocapi.model.dto.DocumentRequestDTO request) {
        DocumentEntity toCreate = DocumentEntity.builder()
                .filename(request.getFilename())
                .contentType(request.getContentType())
                .sizeBytes(request.getSizeBytes())
                .build();

        DocumentEntity created = documentService.createDocument(toCreate);
        return new ResponseEntity<>(at.capadocapi.model.dto.DocumentResponseDTO.from(created), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<at.capadocapi.model.dto.DocumentResponseDTO> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody at.capadocapi.model.dto.DocumentRequestDTO request) {
        try {
            DocumentEntity toUpdate = DocumentEntity.builder()
                    .filename(request.getFilename())
                    .contentType(request.getContentType())
                    .sizeBytes(request.getSizeBytes())
                    .build();

            DocumentEntity updated = documentService.updateDocument(id, toUpdate);
            return ResponseEntity.ok(at.capadocapi.model.dto.DocumentResponseDTO.from(updated));
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