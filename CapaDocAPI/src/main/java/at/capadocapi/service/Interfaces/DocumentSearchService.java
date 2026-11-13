package at.capadocapi.service.Interfaces;

import at.capadocapi.model.dto.DocumentResponseDTO;

import java.util.List;

public interface DocumentSearchService {

    List<DocumentResponseDTO> searchDocuments(String query, int page, int size, String ownerSub);

    default List<DocumentResponseDTO> searchDocuments(String query, int page, int size) {
        return searchDocuments(query, page, size, null);
    }
}
