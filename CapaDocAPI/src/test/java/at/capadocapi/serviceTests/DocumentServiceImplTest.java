package at.capadocapi;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.DocumentServiceImpl;
import at.capadocapi.service.Interfaces.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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


    //  Test: Repository liefert Optional.of(document) → Service gibt dasselbe Document zurück
    @Test
    void getDocumentById_documentExists_returnsDocument() {
        DocumentEntity doc1 = new DocumentEntity();
        doc1.setId(1L);
        doc1.setFilename("test");
        doc1.setUploadedAt(LocalDateTime.now());
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));

        Optional<DocumentEntity> result = documentService.getDocumentById(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(doc1);
        verify(documentRepository).findById(1L);
    }

    //  Test: Repository liefert Optional.empty() → Service gibt Optional.empty() zurück
    //  (kein Exception-Wurf hier, da die Methode Optional zurückgibt!)
    @Test
    void getDocumentById_documentNotFound_returnsEmptyOptional() {
        when(documentRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<DocumentEntity> result = documentService.getDocumentById(1L);

        assertThat(result).isEmpty();
        verify(documentRepository).findById(1L);
    }

    //  Test: übergebenes Document wird gespeichert, Rückgabewert = was repository.save() liefert
    @Test
    void createDocument_validDocument_returnsSavedDocument() {
        // Arrange
        DocumentEntity doc1 = new DocumentEntity();
        doc1.setId(1L);
        doc1.setFilename("test");

        when(documentRepository.save(doc1)).thenReturn(doc1);

        // Act
        DocumentEntity savedDocument = documentService.createDocument(doc1);

        // Assert
        assertThat(savedDocument).isEqualTo(doc1);
        verify(documentRepository).save(doc1);
    }

    //  Test uploadedAt wird gesetzt: Prüfen, dass uploadedAt vor dem Speichern auf einen Wert
    //  nahe LocalDateTime.now() gesetzt wurde (nicht null, nicht der ursprüngliche Wert)
    @Test
    void createDocument_setsUploadedAtTimestamp() {
        // Arrange
        DocumentEntity doc1 = new DocumentEntity();
        doc1.setId(1L);
        doc1.setFilename("test");
        doc1.setUploadedAt(null);

        when(documentRepository.save(doc1)).thenReturn(doc1);

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);

        LocalDateTime before = LocalDateTime.now();

        // Act
        documentService.createDocument(doc1);

        LocalDateTime after = LocalDateTime.now();

        // Assert
        verify(documentRepository).save(captor.capture());
        DocumentEntity capturedDocument = captor.getValue();

        assertThat(capturedDocument.getUploadedAt()).isNotNull();
        assertThat(capturedDocument.getUploadedAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    void updateDocument_documentExists_updatesAndReturnsDocument() {
        // Arrange
        LocalDateTime originalUploadedAt = LocalDateTime.now().minusDays(1);

        DocumentEntity existingDoc = new DocumentEntity();
        existingDoc.setId(1L);
        existingDoc.setFilename("old.pdf");
        existingDoc.setContentType("application/pdf");
        existingDoc.setSizeBytes(100L);
        existingDoc.setUploadedAt(originalUploadedAt);

        DocumentEntity updateInfo = new DocumentEntity();
        updateInfo.setFilename("new.pdf");
        updateInfo.setContentType("application/json");
        updateInfo.setSizeBytes(200L);
        updateInfo.setUploadedAt(LocalDateTime.now()); // Soll nicht übernommen werden

        when(documentRepository.findById(1L)).thenReturn(Optional.of(existingDoc));
        when(documentRepository.save(org.mockito.ArgumentMatchers.any(DocumentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        DocumentEntity updatedDoc = documentService.updateDocument(1L, updateInfo);

        // Assert
        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).findById(1L);
        verify(documentRepository).save(captor.capture());

        DocumentEntity savedDoc = captor.getValue();

        // Geänderte Felder prüfen
        assertThat(savedDoc.getFilename()).isEqualTo("new.pdf");
        assertThat(savedDoc.getContentType()).isEqualTo("application/json");
        assertThat(savedDoc.getSizeBytes()).isEqualTo(200L);

        // Unveränderte Felder prüfen
        assertThat(savedDoc.getId()).isEqualTo(1L);
        assertThat(savedDoc.getUploadedAt()).isEqualTo(originalUploadedAt);

        // Rückgabewert prüfen
        assertThat(updatedDoc).isEqualTo(savedDoc);
    }

    @Test
    void updateDocument_documentNotFound_throwsIllegalArgumentException() {
        // Arrange
        Long nonExistingId = 1L;
        DocumentEntity updateInfo = new DocumentEntity();
        when(documentRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> documentService.updateDocument(nonExistingId, updateInfo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(nonExistingId));

        verify(documentRepository).findById(nonExistingId);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void deleteDocument_validId_callsRepositoryDeleteById() {
        // Arrange
        Long id = 1L;

        // Act
        documentService.deleteDocument(id);

        // Assert
        verify(documentRepository).deleteById(id);
    }

    @Test
    void deleteDocument_idNotFound_behaviorToBeDefined() {
        // Arrange
        Long nonExistingId = 99L;
        doThrow(new EmptyResultDataAccessException(1))
                .when(documentRepository).deleteById(nonExistingId);

        // Act & Assert
        assertThatThrownBy(() -> documentService.deleteDocument(nonExistingId))
                .isInstanceOf(EmptyResultDataAccessException.class);

        verify(documentRepository).deleteById(nonExistingId);
    }

    @Test
    void createDocument_nullDocument_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> documentService.createDocument(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DocumentEntity must not be null");

        verify(documentRepository, never()).save(any());
    }
}
