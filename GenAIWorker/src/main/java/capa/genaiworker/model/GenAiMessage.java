package capa.genaiworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenAiMessage {
    private Long documentId;
    private String filename;
    private String extractedText;
}
