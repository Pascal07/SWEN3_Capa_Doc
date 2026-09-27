package at.capadocapi.controller;

import at.capadocapi.mapper.DocumentMapper;
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
    private final DocumentMapper documentMapper;

    @GetMapping
    public ResponseEntity<List<DocumentResponseDTO>> getAllDocuments() {
        List<DocumentResponseDTO> response = documentService.getAllDocuments()
                .stream()
                .map(documentMapper::toResponseDto)
                .toList();
        return ResponseEntity.ok(response); // ist Kurzform für: new ResponseEntity<>(response, HttpStatus.OK)
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getDocumentById(@PathVariable Long id) {
        return documentService.getDocumentById(id)
                .map(documentMapper::toResponseDto) // Entity → DTO
                .map(ResponseEntity::ok) // DTO → ResponseEntity mit Status 200
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DocumentResponseDTO> createDocument(@Valid @RequestBody DocumentRequestDTO request) {
        DocumentEntity toCreate = documentMapper.toEntity(request); // DTO → Entity (in)
        DocumentEntity created = documentService.createDocument(toCreate); // Service arbeitet mit Entity
        return new ResponseEntity<>(documentMapper.toResponseDto(created), HttpStatus.CREATED); // Entity → DTO (out)
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequestDTO request) {
        try {
            DocumentEntity toUpdate = documentMapper.toEntity(request);
            DocumentEntity updated = documentService.updateDocument(id, toUpdate);
            return ResponseEntity.ok(documentMapper.toResponseDto(updated));
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