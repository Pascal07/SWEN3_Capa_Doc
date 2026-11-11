package at.capadoc.indexworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexedDocument {
    private String documentId;
    private String filename;
    private String content;
    private String uploadedAt;
}
