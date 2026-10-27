package at.capadoc.ocrworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrMessage {
    private Long documentId;
    private String filename;
    private String contentType;
    private LocalDateTime uploadedAt;
}