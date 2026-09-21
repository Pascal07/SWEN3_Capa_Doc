package at.capadocapi.model.dto;

import at.capadocapi.model.DocumentEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Used as the response body for /documents endpoints.
// Deliberately does NOT expose entity internals (e.g. the shareLinks collection),
// so there's no lazy-loading/serialization surprise once ShareLink is wired up.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponseDTO {

    private Long id;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime uploadedAt;

    public static at.capadocapi.model.dto.DocumentResponseDTO from(DocumentEntity documentEntity) {
        return at.capadocapi.model.dto.DocumentResponseDTO.builder()
                .id(documentEntity.getId())
                .filename(documentEntity.getFilename())
                .contentType(documentEntity.getContentType())
                .sizeBytes(documentEntity.getSizeBytes())
                .uploadedAt(documentEntity.getUploadedAt())
                .build();
    }
}
