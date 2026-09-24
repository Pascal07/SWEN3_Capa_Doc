package at.capadocapi.service;

import at.capadocapi.model.dto.DocumentResponseDTO;
import at.capadocapi.model.dto.ShareLinkRequestDTO;
import at.capadocapi.model.dto.ShareLinkResponseDTO;
import at.capadocapi.exception.InvalidShareLinkPasswordException;
import at.capadocapi.exception.ShareLinkExpiredException;
import at.capadocapi.exception.ShareLinkNotFoundException;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ShareLinkEntity;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.repository.ShareLinkRepository;
import at.capadocapi.service.Interfaces.ShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShareLinkServiceImpl implements ShareLinkService {

    private static final String SHORT_CODE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHORT_CODE_LENGTH = 8;

    private final ShareLinkRepository shareLinkRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    // POST /documents/{documentId}/links or /api/links/create/{documentId}
    @Override
    @Transactional
    public ShareLinkResponseDTO createShareLink(Long documentId, ShareLinkRequestDTO request) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + documentId));

        ShareLinkEntity link = ShareLinkEntity.builder()
                .shortCode(generateUniqueShortCode())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .expiryDate(request.getExpiryDate())
                .build();

        ShareLinkEntity savedLink = shareLinkRepository.save(link);

        // DocumentEntity owns the join table, so the link must be added on that side
        document.getShareLinks().add(savedLink);
        documentRepository.save(document);

        return ShareLinkResponseDTO.from(savedLink);
    }

    // GET /links/{shortCode}?password=...
    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> resolveLink(String shortCode, String password) {
        ShareLinkEntity link = shareLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShareLinkNotFoundException(shortCode));

        if (link.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ShareLinkExpiredException(shortCode);
        }

        if (!passwordEncoder.matches(password, link.getPasswordHash())) {
            throw new InvalidShareLinkPasswordException();
        }

        return link.getDocuments().stream()
                .map(DocumentResponseDTO::from)
                .toList();
    }

    // DELETE /links/{id}
    @Override
    @Transactional
    public void deleteShareLink(Long id) {
        ShareLinkEntity link = shareLinkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Share link not found with id: " + id));

        // remove the association from the owning side first, otherwise the
        // join-table foreign key stops the delete from succeeding
        for (DocumentEntity document : link.getDocuments()) {
            document.getShareLinks().remove(link);
            documentRepository.save(document);
        }

        shareLinkRepository.delete(link);
    }

    private String generateUniqueShortCode() {
        String shortCode;
        do {
            shortCode = generateRandomShortCode();
        } while (shareLinkRepository.existsByShortCode(shortCode));
        return shortCode;
    }

    private String generateRandomShortCode() {
        StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            sb.append(SHORT_CODE_ALPHABET.charAt(random.nextInt(SHORT_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
