package at.capadocapi.controller;

import at.capadocapi.exception.DocumentNotFoundException;
import at.capadocapi.mapper.DocumentMapper;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.dto.DocumentRequestDTO;
import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.service.DocumentServiceImpl;
import at.capadocapi.service.Interfaces.DocumentSearchService;
import at.capadocapi.service.Interfaces.DocumentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping({"/api/documents", "/documents"})
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;
    private final DocumentSearchService documentSearchService;

    @Autowired
    public DocumentController(
            DocumentService documentService,
            DocumentMapper documentMapper,
            DocumentSearchService documentSearchService) {
        this.documentService = documentService;
        this.documentMapper = documentMapper;
        this.documentSearchService = documentSearchService;
    }

    public DocumentController(
            DocumentService documentService,
            DocumentMapper documentMapper) {
        this(documentService, documentMapper, null);
    }

    @GetMapping("/search")
    public ResponseEntity<List<DocumentResponseDTO>> searchDocuments(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal OidcUser user) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query 'q' must not be blank");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than 0");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        String ownerSub = (user != null) ? user.getSubject() : null;
        List<DocumentResponseDTO> results = documentSearchService.searchDocuments(query, page, size, ownerSub);
        return ResponseEntity.ok(results);
    }

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
            throw new DocumentNotFoundException(id);
        }
        DocumentEntity document = documentOpt.get();
        if (isForbidden(document, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(documentMapper.toResponseDto(document));
    }

    @GetMapping({"/{id}/download", "/{id}/file"})
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {
        Optional<DocumentEntity> documentOpt = documentService.getDocumentById(id);
        if (documentOpt.isEmpty()) {
            throw new DocumentNotFoundException(id);
        }
        DocumentEntity document = documentOpt.get();
        if (isForbidden(document, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        byte[] content = documentService.downloadDocument(id);
        String filename = (document.getFilename() != null && !document.getFilename().isBlank())
                ? document.getFilename()
                : "document-" + id + ".pdf";
        String contentType = (document.getContentType() != null && !document.getContentType().isBlank())
                ? document.getContentType()
                : DocumentServiceImpl.PDF_CONTENT_TYPE;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(content.length);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
        );

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponseDTO> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "metadata", required = false) @Valid DocumentRequestDTO metadata,
            @RequestParam(value = "filename", required = false) String filenameParam,
            @AuthenticationPrincipal OidcUser user) {
        byte[] pdfBytes = DocumentServiceImpl.validateAndReadPdfFile(file);

        DocumentEntity toCreate = (metadata != null)
                ? documentMapper.toEntity(metadata)
                : new DocumentEntity();

        if (filenameParam != null && !filenameParam.isBlank()) {
            toCreate.setFilename(filenameParam);
        } else if (toCreate.getFilename() == null || toCreate.getFilename().isBlank()) {
            String originalFilename = file.getOriginalFilename();
            toCreate.setFilename(
                    (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "document.pdf"
            );
        }
        toCreate.setContentType(DocumentServiceImpl.PDF_CONTENT_TYPE);
        toCreate.setSizeBytes((long) pdfBytes.length);

        if (user != null) {
            toCreate.setOwnerSub(user.getSubject());
        }

        DocumentEntity created = documentService.createDocument(toCreate, file);
        return new ResponseEntity<>(documentMapper.toResponseDto(created), HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
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
            throw new DocumentNotFoundException(id);
        }
        if (isForbidden(existingOpt.get(), user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        DocumentEntity toUpdate = documentMapper.toEntity(request);
        DocumentEntity updated = documentService.updateDocument(id, toUpdate);
        return ResponseEntity.ok(documentMapper.toResponseDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {
        Optional<DocumentEntity> existingOpt = documentService.getDocumentById(id);
        if (existingOpt.isEmpty()) {
            throw new DocumentNotFoundException(id);
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
