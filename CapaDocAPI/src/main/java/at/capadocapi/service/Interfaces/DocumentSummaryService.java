package at.capadocapi.service.Interfaces;

public interface DocumentSummaryService {

    /**
     * Speichert die von GenAI erzeugte Zusammenfassung für das angegebene Dokument.
     * Existiert das Dokument nicht mehr, wird die Nachricht sauber ignoriert.
     */
    void saveSummary(Long documentId, String summary);
}
