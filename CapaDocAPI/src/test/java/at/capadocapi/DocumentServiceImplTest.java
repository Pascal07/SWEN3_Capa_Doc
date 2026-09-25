package at.capadocapi;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.DocumentServiceImpl;
import at.capadocapi.service.Interfaces.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceImplTest {
    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    void getAllDocuments_documentsExist_returnsAllDocuments() {
        // Arrange
        DocumentEntity doc1 = new DocumentEntity();
        doc1.setId(1L);
        doc1.setFilename("test1.pdf");

        DocumentEntity doc2 = new DocumentEntity();
        doc2.setId(2L);
        doc2.setFilename("test2.pdf");

        when(documentRepository.findAll()).thenReturn(List.of(doc1, doc2));

        // Act
        List<DocumentEntity> result = documentService.getAllDocuments();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(doc1, doc2);
        verify(documentRepository).findAll();
    }

    @Test
    void getAllDocuments_noDocumentsExist_returnsEmptyList() {
        // Arrange
        when(documentRepository.findAll()).thenReturn(List.of());

        // Act
        List<DocumentEntity> result = documentService.getAllDocuments();

        // Assert
        assertThat(result).isEmpty();
        verify(documentRepository).findAll();
    }

    // Todo: getDocumentById(Long id) Test
    //  Gefunden: Repository liefert Optional.of(document) → Service gibt dasselbe Document zurück
    @Test
    void getDocumentById_documentExists_returnsDocument() {
    }

    // Todo: getDocumentById(Long id) Test
    //  Nicht gefunden: Repository liefert Optional.empty() → Service gibt Optional.empty() zurück
    //  (kein Exception-Wurf hier, da die Methode Optional zurückgibt!)
    @Test
    void getDocumentById_documentNotFound_returnsEmptyOptional() {
    }

    // Todo: createDocument(DocumentEntity) Test
    //  Happy Path: übergebenes Document wird gespeichert, Rückgabewert = was repository.save() liefert
    @Test
    void createDocument_validDocument_returnsSavedDocument() {
    }

    // Todo: createDocument(DocumentEntity) Test
    //  uploadedAt wird gesetzt: Prüfen, dass uploadedAt vor dem Speichern auf einen Wert
    //  nahe LocalDateTime.now() gesetzt wurde (nicht null, nicht der ursprüngliche Wert)
    //  Tipp: ArgumentCaptor verwenden, um zu prüfen, was tatsächlich an save() übergeben wurde
    @Test
    void createDocument_setsUploadedAtTimestamp() {
    }

    // Todo: updateDocument(Long id, DocumentEntity) Test
    //  Happy Path (gefunden):
    //  - Repository liefert existierendes Document via findById
    //  - Prüfen, dass filename, contentType, sizeBytes vom updatedDocumentEntity übernommen wurden
    //  - Prüfen, dass andere Felder (z.B. id, uploadedAt) vom existierenden Document
    //    unverändert bleiben (nicht überschrieben!)
    //  - Prüfen, dass save() mit dem gemergten Objekt aufgerufen wurde
    @Test
    void updateDocument_documentExists_updatesAndReturnsDocument() {
    }

    // Todo: updateDocument(Long id, DocumentEntity) Test
    //  Nicht gefunden: findById liefert Optional.empty() → erwarte IllegalArgumentException
    //  - Prüfen, dass die Exception-Message die ID enthält
    //  - Prüfen, dass save() in diesem Fall niemals aufgerufen wird (verify(..., never()))
    @Test
    void updateDocument_documentNotFound_throwsIllegalArgumentException() {
    }

    // Todo: deleteDocument(Long id) Test
    //  Happy Path: deleteById(id) wird mit der richtigen ID aufgerufen
    @Test
    void deleteDocument_validId_callsRepositoryDeleteById() {
    }

    // Todo: deleteDocument(Long id) Test
    //  Überlegen: Was passiert aktuell, wenn die ID nicht existiert?
    //  (JpaRepository.deleteById() wirft ggf. EmptyResultDataAccessException –
    //  wird das im Service abgefangen? Falls nicht: Lücke notieren, ggf. Service anpassen)
    @Test
    void deleteDocument_idNotFound_behaviorToBeDefined() {
    }

    // Todo: Sonderfälle / Edge Cases
    //  - createDocument(null) bzw. Document mit null-Feldern: wird das aktuell abgefangen?
    // Todo: Am Ende
    //  - mvn test + Coverage-Tool (z.B. JaCoCo) laufen lassen und 70%-Grenze prüfen
}
