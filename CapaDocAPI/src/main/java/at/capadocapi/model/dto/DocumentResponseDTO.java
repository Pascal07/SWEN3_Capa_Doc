package at.capadocapi.model.dto;

import at.capadocapi.model.ProcessingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    private String ownerSub;
    private String storageKey;
    private ProcessingStatus ocrStatus;
    private ProcessingStatus indexStatus;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String errorMessage;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String snippet;

}
