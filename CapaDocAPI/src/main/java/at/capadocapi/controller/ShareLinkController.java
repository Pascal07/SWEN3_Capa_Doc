package at.capadocapi.controller;

import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;
import at.capadocapi.service.DocumentServiceImpl;
import at.capadocapi.service.Interfaces.DocumentService;
import at.capadocapi.service.ShareLinkServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkServiceImpl shareLinkService;

    @Autowired(required = false)
    private DocumentService documentService;

    @PostMapping({"/create/{documentId}", "/create/{documentId}/"})
    public ResponseEntity<ShareLinkResponseDTO> createShareLink(
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody ShareLinkRequestDTO request) {
        ShareLinkResponseDTO response = shareLinkService.createShareLink(documentId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<ShareLinkResponseDTO>> getShareLinksForDocument(
            @PathVariable("documentId") Long documentId) {
        return ResponseEntity.ok(shareLinkService.getShareLinksForDocument(documentId));
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
    public ResponseEntity<byte[]> downloadSharedDocument(
            @PathVariable("shortCode") String shortCode,
            @RequestParam("password") String password) {
        List<DocumentResponseDTO> documents = shareLinkService.resolveLink(shortCode, password);
        if (documents.isEmpty() || documentService == null) {
            return ResponseEntity.notFound().build();
        }
        DocumentResponseDTO doc = documents.getFirst();
        byte[] content = documentService.downloadDocument(doc.getId());
        String filename = (doc.getFilename() != null && !doc.getFilename().isBlank())
                ? doc.getFilename()
                : "document-" + doc.getId() + ".pdf";
        String contentType = (doc.getContentType() != null && !doc.getContentType().isBlank())
                ? doc.getContentType()
                : DocumentServiceImpl.PDF_CONTENT_TYPE;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(content.length);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
        );

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShareLink(@PathVariable("id") Long id) {
        shareLinkService.deleteShareLink(id);
        return ResponseEntity.noContent().build();
    }
}
