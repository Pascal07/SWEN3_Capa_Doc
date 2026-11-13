package at.capadocapi.service.Interfaces;

import at.capadocapi.model.dto.DocumentStatusEvent;

public interface DocumentStatusService {

    void updateDocumentStatus(DocumentStatusEvent event);
}
