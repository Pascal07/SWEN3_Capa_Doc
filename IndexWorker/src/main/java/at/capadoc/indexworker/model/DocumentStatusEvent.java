package at.capadoc.indexworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentStatusEvent {

    private Long documentId;
    private ProcessingStage stage;
    private ProcessingStatus status;
    private String errorMessage;
    private LocalDateTime timestamp;
}
