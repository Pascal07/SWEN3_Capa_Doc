package at.capadocapi.model.dto;

import at.capadocapi.model.DocumentEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

}
