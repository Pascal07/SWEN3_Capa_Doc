package at.capadocapi.controller;

import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;
import at.capadocapi.service.ShareLinkServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkServiceImpl shareLinkService;

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShareLink(@PathVariable("id") Long id) {
        shareLinkService.deleteShareLink(id);
        return ResponseEntity.noContent().build();
    }
}
