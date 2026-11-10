package at.capadocapi;

import at.capadocapi.exception.StorageException;
import at.capadocapi.exception.StorageObjectNotFoundException;
import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.DocumentServiceImpl;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import at.capadocapi.service.Interfaces.DocumentService;
import at.capadocapi.service.Interfaces.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
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

    @Mock
    private DocumentMessagePublisher messagePublisher;

    @Mock
    private StorageService storageService;

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

    @Test
    void getDocumentsByOwner_documentsExist_returnsFilteredDocuments() {
        DocumentEntity doc1 = new DocumentEntity();
        doc1.setId(1L);
        doc1.setFilename("test1.pdf");
        doc1.setOwnerSub("sub-123");

        when(documentRepository.findByOwnerSub("sub-123")).thenReturn(List.of(doc1));

        List<DocumentEntity> result = documentService.getDocumentsByOwner("sub-123");

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly(doc1);
        verify(documentRepository).findByOwnerSub("sub-123");
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

    @Test
    void createDocument_validDocument_publishesOcrMessageAfterSaving() {
        DocumentEntity document = new DocumentEntity();
        document.setId(7L);
        document.setFilename("report.pdf");
        document.setContentType("application/pdf");
        document.setSizeBytes(1024L);

        when(documentRepository.save(document)).thenReturn(document);

        documentService.createDocument(document);

        ArgumentCaptor<OcrMessage> messageCaptor = ArgumentCaptor.forClass(OcrMessage.class);
        verify(messagePublisher).publishOcrMessage(messageCaptor.capture());
        OcrMessage message = messageCaptor.getValue();
        assertThat(message.getDocumentId()).isEqualTo(7L);
        assertThat(message.getFilename()).isEqualTo("report.pdf");
        assertThat(message.getContentType()).isEqualTo("application/pdf");
        assertThat(message.getUploadedAt()).isEqualTo(document.getUploadedAt());
        verify(documentRepository).save(document);
    }

    @Test
    void createDocument_publisherFails_propagatesPublishingException() {
        DocumentEntity document = new DocumentEntity();
        document.setId(7L);
        document.setFilename("report.pdf");
        when(documentRepository.save(document)).thenReturn(document);
        RuntimeException publishingFailure = new RuntimeException("broker unavailable");
        doThrow(publishingFailure).when(messagePublisher).publishOcrMessage(any(OcrMessage.class));

        assertThatThrownBy(() -> documentService.createDocument(document))
                .isSameAs(publishingFailure);

        verify(documentRepository).save(document);
        verify(messagePublisher).publishOcrMessage(any(OcrMessage.class));
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

    @Test
    void createDocumentWithFile_validPdf_uploadsToRustFsSetsStorageKeyAndPublishesMessage() {
        byte[] pdfBytes = "%PDF-1.7 valid pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", pdfBytes);

        DocumentEntity input = new DocumentEntity();
        input.setFilename("invoice.pdf");

        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(inv -> {
            DocumentEntity doc = inv.getArgument(0);
            if (doc.getId() == null) {
                doc.setId(42L);
            }
            return doc;
        });

        DocumentEntity saved = documentService.createDocument(input, pdfFile);

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getStorageKey()).isEqualTo("documents/42.pdf");
        assertThat(saved.getOcrStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(saved.getIndexStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(saved.getSizeBytes()).isEqualTo((long) pdfBytes.length);
        assertThat(saved.getContentType()).isEqualTo("application/pdf");

        verify(storageService).upload("documents/42.pdf", pdfBytes, "application/pdf");

        ArgumentCaptor<OcrMessage> msgCaptor = ArgumentCaptor.forClass(OcrMessage.class);
        verify(messagePublisher).publishOcrMessage(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getDocumentId()).isEqualTo(42L);
        assertThat(msgCaptor.getValue().getStorageKey()).isEqualTo("documents/42.pdf");
    }

    @Test
    void createDocumentWithFile_whenTransactionActive_publishesMessageOnlyAfterCommit() {
        byte[] pdfBytes = "%PDF-1.7 valid pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", pdfBytes);
        DocumentEntity input = new DocumentEntity();
        input.setFilename("invoice.pdf");

        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(inv -> {
            DocumentEntity doc = inv.getArgument(0);
            if (doc.getId() == null) {
                doc.setId(15L);
            }
            return doc;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.createDocument(input, pdfFile);

            // Vor dem Commit darf noch keine Queue-Nachricht gesendet worden sein
            verify(messagePublisher, never()).publishOcrMessage(any());

            // Simuliere erfolgreichen Transaction-Commit
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            ArgumentCaptor<OcrMessage> msgCaptor = ArgumentCaptor.forClass(OcrMessage.class);
            verify(messagePublisher, times(1)).publishOcrMessage(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getDocumentId()).isEqualTo(15L);
            assertThat(msgCaptor.getValue().getStorageKey()).isEqualTo("documents/15.pdf");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createDocumentWithFile_whenUploadToRustFsFails_propagatesExceptionAndDoesNotPublishMessage() {
        byte[] pdfBytes = "%PDF-1.7 valid pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", pdfBytes);
        DocumentEntity input = new DocumentEntity();
        input.setFilename("invoice.pdf");

        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(inv -> {
            DocumentEntity doc = inv.getArgument(0);
            doc.setId(99L);
            return doc;
        });
        doThrow(new StorageException("RustFS down"))
                .when(storageService).upload(anyString(), any(byte[].class), anyString());

        assertThatThrownBy(() -> documentService.createDocument(input, pdfFile))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("RustFS down");

        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        verify(messagePublisher, never()).publishOcrMessage(any());
    }

    @Test
    void createDocumentWithFile_whenSecondSaveFails_deletesUploadedObjectFromRustFs() {
        byte[] pdfBytes = "%PDF-1.7 valid pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", pdfBytes);
        DocumentEntity input = new DocumentEntity();
        input.setFilename("invoice.pdf");

        when(documentRepository.save(any(DocumentEntity.class)))
                .thenAnswer(inv -> {
                    DocumentEntity doc = inv.getArgument(0);
                    doc.setId(55L);
                    return doc;
                })
                .thenThrow(new DataRetrievalFailureException("DB failure on second save"));

        assertThatThrownBy(() -> documentService.createDocument(input, pdfFile))
                .isInstanceOf(EmptyResultDataAccessException.class);

        verify(storageService).upload("documents/55.pdf", pdfBytes, "application/pdf");
        verify(storageService).delete("documents/55.pdf");
        verify(messagePublisher, never()).publishOcrMessage(any());
    }

    @Test
    void createDocumentWithFile_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> documentService.createDocument(new DocumentEntity(), emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        verifyNoInteractions(documentRepository, storageService, messagePublisher);
    }

    @Test
    void createDocumentWithFile_nonPdfContentType_throwsIllegalArgumentException() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> documentService.createDocument(new DocumentEntity(), textFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application/pdf");

        verifyNoInteractions(documentRepository, storageService, messagePublisher);
    }

    @Test
    void createDocumentWithFile_invalidMagicBytes_throwsIllegalArgumentException() {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "NOT_A_PDF_FILE".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> documentService.createDocument(new DocumentEntity(), fakePdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("%PDF");

        verifyNoInteractions(documentRepository, storageService, messagePublisher);
    }

    @Test
    void deleteDocument_withStorageKey_deletesFromStorageAndRepository() {
        DocumentEntity doc = new DocumentEntity();
        doc.setId(10L);
        doc.setStorageKey("documents/10.pdf");
        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));

        documentService.deleteDocument(10L);

        verify(storageService).delete("documents/10.pdf");
        verify(documentRepository).deleteById(10L);
    }

    @Test
    void deleteDocument_whenObjectMissingInRustFs_stillDeletesFromRepository() {
        DocumentEntity doc = new DocumentEntity();
        doc.setId(11L);
        doc.setStorageKey("documents/11.pdf");
        when(documentRepository.findById(11L)).thenReturn(Optional.of(doc));
        doThrow(new StorageObjectNotFoundException("documents/11.pdf"))
                .when(storageService).delete("documents/11.pdf");

        documentService.deleteDocument(11L);

        verify(storageService).delete("documents/11.pdf");
        verify(documentRepository).deleteById(11L);
    }
}
