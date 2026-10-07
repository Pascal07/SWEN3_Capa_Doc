package at.capadocapi.controller;

import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.DocumentContentEntity;
import at.capadocapi.service.Interfaces.DocumentService;
import at.capadocapi.service.ShareLinkServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/links", "/links"})
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkServiceImpl shareLinkService;
    private final DocumentService documentService;

    @PostMapping({"/create/{documentId}", "/create/{documentId}/"})
    public ResponseEntity<ShareLinkResponseDTO> createShareLink(
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody ShareLinkRequestDTO request) {
        ShareLinkResponseDTO response = shareLinkService.createShareLink(documentId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<DocumentResponseDTO> resolveLink(
            @PathVariable("shortCode") String shortCode,
            @RequestParam("password") String password) {
        List<DocumentResponseDTO> documents = shareLinkService.resolveLink(shortCode, password);
        if (documents.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(documents.getFirst());
    }

    @GetMapping("/{shortCode}/download")
    public ResponseEntity<ByteArrayResource> downloadSharedDocument(
            @PathVariable("shortCode") String shortCode,
            @RequestParam("password") String password) {
        DocumentEntity document = shareLinkService.getDocumentForDownload(shortCode, password);
        DocumentContentEntity content = documentService.getDocumentContent(document.getId())
                .orElseThrow(() -> new at.capadocapi.exception.ShareLinkNotFoundException(shortCode));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.getFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(content.getContent().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new ByteArrayResource(content.getContent()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShareLink(@PathVariable("id") Long id) {
        shareLinkService.deleteShareLink(id);
        return ResponseEntity.noContent().build();
    }
}
