package capa.genaiworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentStatusEvent {
    private Long documentId;
    private ProcessingStage stage;
    private ProcessingStatus status;
    private String errorMessage;
    private Instant timestamp;
}
