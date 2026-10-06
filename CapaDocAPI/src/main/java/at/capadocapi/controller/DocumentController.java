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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping({"/api/documents", "/documents"})
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;

    @GetMapping
    public ResponseEntity<List<DocumentResponseDTO>> getAllDocuments(
            @AuthenticationPrincipal OidcUser user) {
        List<DocumentEntity> documents = (user != null)
                ? documentService.getDocumentsByOwner(user.getSubject())
                : documentService.getAllDocuments();

        List<DocumentResponseDTO> response = documents.stream()
                .map(documentMapper::toResponseDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getDocumentById(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {
        Optional<DocumentEntity> documentOpt = documentService.getDocumentById(id);
        if (documentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DocumentEntity document = documentOpt.get();
        if (isForbidden(document, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(documentMapper.toResponseDto(document));
    }

    @PostMapping
    public ResponseEntity<DocumentResponseDTO> createDocument(
            @Valid @RequestBody DocumentRequestDTO request,
            @AuthenticationPrincipal OidcUser user) {
        DocumentEntity toCreate = documentMapper.toEntity(request);
        if (user != null) {
            toCreate.setOwnerSub(user.getSubject());
        }
        DocumentEntity created = documentService.createDocument(toCreate);
        return new ResponseEntity<>(documentMapper.toResponseDto(created), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequestDTO request,
            @AuthenticationPrincipal OidcUser user) {
        Optional<DocumentEntity> existingOpt = documentService.getDocumentById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (isForbidden(existingOpt.get(), user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            DocumentEntity toUpdate = documentMapper.toEntity(request);
            DocumentEntity updated = documentService.updateDocument(id, toUpdate);
            return ResponseEntity.ok(documentMapper.toResponseDto(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {
        Optional<DocumentEntity> existingOpt = documentService.getDocumentById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (isForbidden(existingOpt.get(), user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isForbidden(DocumentEntity doc, OidcUser user) {
        if (user == null) {
            return false;
        }
        return doc.getOwnerSub() != null && !doc.getOwnerSub().equals(user.getSubject());
    }
}