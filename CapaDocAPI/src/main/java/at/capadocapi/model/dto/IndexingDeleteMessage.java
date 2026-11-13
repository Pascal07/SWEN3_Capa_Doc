package at.capadocapi.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexingDeleteMessage {
    private Long documentId;
    private String action;

    public static IndexingDeleteMessage of(Long documentId) {
        return new IndexingDeleteMessage(documentId, "DELETE");
    }
}
