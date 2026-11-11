package at.capadocapi.serviceTests;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStage;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.model.dto.DocumentStatusEvent;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.DocumentStatusListener;
import at.capadocapi.service.DocumentStatusServiceImpl;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentStatusServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMessagePublisher messagePublisher;

    private DocumentStatusServiceImpl statusService;
    private DocumentStatusListener statusListener;
    private DocumentEntity existingDocument;

    @BeforeEach
    void setUp() {
        statusService = new DocumentStatusServiceImpl(documentRepository);
        statusListener = new DocumentStatusListener(statusService, messagePublisher);

        existingDocument = DocumentEntity.builder()
                .id(42L)
                .filename("HelloWorld.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024L)
                .uploadedAt(LocalDateTime.of(2026, 11, 11, 12, 0))
                .ocrStatus(ProcessingStatus.PENDING)
                .indexStatus(ProcessingStatus.PENDING)
                .build();
    }

    @Test
    void handleStatusEvent_ocrDone_updatesOcrStatusToDone() {
        when(documentRepository.findById(42L)).thenReturn(Optional.of(existingDocument));

        DocumentStatusEvent event = DocumentStatusEvent.builder()
                .documentId(42L)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.DONE)
                .timestamp(LocalDateTime.now())
                .build();

        statusListener.handleDocumentStatusEvent(event);

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getOcrStatus()).isEqualTo(ProcessingStatus.DONE);
        assertThat(captor.getValue().getIndexStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(captor.getValue().getErrorMessage()).isNull();
        verify(messagePublisher, never()).publishInvalidStatusEventToDlq(any());
    }

    @Test
    void handleStatusEvent_indexingDone_updatesIndexStatusToDone() {
        existingDocument.setOcrStatus(ProcessingStatus.DONE);
        when(documentRepository.findById(42L)).thenReturn(Optional.of(existingDocument));

        DocumentStatusEvent event = DocumentStatusEvent.builder()
                .documentId(42L)
                .stage(ProcessingStage.INDEXING)
                .status(ProcessingStatus.DONE)
                .timestamp(LocalDateTime.now())
                .build();

        statusListener.handleDocumentStatusEvent(event);

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getOcrStatus()).isEqualTo(ProcessingStatus.DONE);
        assertThat(captor.getValue().getIndexStatus()).isEqualTo(ProcessingStatus.DONE);
        verify(messagePublisher, never()).publishInvalidStatusEventToDlq(any());
    }

    @Test
    void handleStatusEvent_failedWithErrorMessage_setsFailedAndStoresErrorMessage() {
        when(documentRepository.findById(42L)).thenReturn(Optional.of(existingDocument));

        DocumentStatusEvent event = DocumentStatusEvent.builder()
                .documentId(42L)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.FAILED)
                .errorMessage("Ghostscript failed: corrupt PDF")
                .timestamp(LocalDateTime.now())
                .build();

        statusListener.handleDocumentStatusEvent(event);

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getOcrStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("Ghostscript failed: corrupt PDF");
        verify(messagePublisher, never()).publishInvalidStatusEventToDlq(any());
    }

    @Test
    void handleStatusEvent_duplicateDoneEvent_isIdempotentAndDoesNotSaveTwice() {
        when(documentRepository.findById(42L)).thenReturn(Optional.of(existingDocument));

        DocumentStatusEvent event = DocumentStatusEvent.builder()
                .documentId(42L)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.DONE)
                .timestamp(LocalDateTime.now())
                .build();

        statusListener.handleDocumentStatusEvent(event);
        statusListener.handleDocumentStatusEvent(event);

        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        assertThat(existingDocument.getOcrStatus()).isEqualTo(ProcessingStatus.DONE);
    }

    @Test
    void handleStatusEvent_delayedEventAfterDone_doesNotRevertStatusToInProgressOrFailed() {
        existingDocument.setOcrStatus(ProcessingStatus.DONE);
        when(documentRepository.findById(42L)).thenReturn(Optional.of(existingDocument));

        DocumentStatusEvent delayedInProgress = DocumentStatusEvent.builder()
                .documentId(42L)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.IN_PROGRESS)
                .timestamp(LocalDateTime.now())
                .build();

        DocumentStatusEvent delayedFailed = DocumentStatusEvent.builder()
                .documentId(42L)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.FAILED)
                .errorMessage("Late error")
                .timestamp(LocalDateTime.now())
                .build();

        statusListener.handleDocumentStatusEvent(delayedInProgress);
        statusListener.handleDocumentStatusEvent(delayedFailed);

        verify(documentRepository, never()).save(any(DocumentEntity.class));
        assertThat(existingDocument.getOcrStatus()).isEqualTo(ProcessingStatus.DONE);
        assertThat(existingDocument.getErrorMessage()).isNull();
    }

    @Test
    void handleStatusEvent_unknownDocumentId_logsAndAcknowledgesWithoutSavingOrThrowing() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        DocumentStatusEvent event = DocumentStatusEvent.builder()
                .documentId(999L)
                .stage(ProcessingStage.INDEXING)
                .status(ProcessingStatus.DONE)
                .timestamp(LocalDateTime.now())
                .build();

        assertThatCode(() -> statusListener.handleDocumentStatusEvent(event))
                .doesNotThrowAnyException();

        verify(documentRepository, never()).save(any());
        verify(messagePublisher, never()).publishInvalidStatusEventToDlq(any());
    }

    @Test
    void handleStatusEvent_invalidMessageMissingFields_routesToDeadLetterQueueWithoutCrash() {
        DocumentStatusEvent missingStage = DocumentStatusEvent.builder()
                .documentId(42L)
                .stage(null)
                .status(ProcessingStatus.DONE)
                .build();

        DocumentStatusEvent missingDocId = DocumentStatusEvent.builder()
                .documentId(null)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.DONE)
                .build();

        assertThatCode(() -> {
            statusListener.handleDocumentStatusEvent(missingStage);
            statusListener.handleDocumentStatusEvent(missingDocId);
            statusListener.handleDocumentStatusEvent(null);
        }).doesNotThrowAnyException();

        verify(documentRepository, never()).findById(any());
        verify(documentRepository, never()).save(any());
        verify(messagePublisher, times(3)).publishInvalidStatusEventToDlq(any());
    }
}
