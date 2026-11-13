package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.OcrProperties;
import at.capadoc.ocrworker.exception.OcrPermanentException;
import at.capadoc.ocrworker.exception.OcrTransientException;
import at.capadoc.ocrworker.exception.StorageObjectNotFoundException;
import at.capadoc.ocrworker.model.DocumentStatusEvent;
import at.capadoc.ocrworker.model.IndexingMessage;
import at.capadoc.ocrworker.model.OcrMessage;
import at.capadoc.ocrworker.model.ProcessingStage;
import at.capadoc.ocrworker.model.ProcessingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrMessageListenerTest {

    @Mock
    private StorageService storageService;

    @Mock
    private OcrService ocrService;

    @Mock
    private IndexingMessagePublisher indexingMessagePublisher;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private StatusPublisher statusPublisher;
    private OcrProperties ocrProperties;
    private OcrMessageListener listener;

    @BeforeEach
    void setUp() {
        ocrProperties = new OcrProperties();
        ocrProperties.setMaxRetries(3);
        ocrProperties.setRetryBackoffMs(5L); // fast backoff for unit tests

        statusPublisher = spy(new RabbitMqStatusPublisher(
                rabbitTemplate,
                "document.status.exchange",
                "document.status.routingkey"
        ));

        listener = new OcrMessageListener(
                storageService,
                ocrService,
                indexingMessagePublisher,
                statusPublisher,
                ocrProperties
        );
    }

    @Test
    void handleOcrMessage_acceptsReceivedMessage() {
        OcrMessageListener standaloneListener = new OcrMessageListener();
        OcrMessage message = new OcrMessage(
                12L,
                "report.pdf",
                "application/pdf",
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );

        assertThatCode(() -> standaloneListener.handleOcrMessage(message))
                .doesNotThrowAnyException();
    }

    @Test
    void handleOcrMessage_successFlow_publishesIndexingMessageBeforeDoneStatusEvent() {
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 5, 1, 12, 0);
        OcrMessage message = new OcrMessage(
                42L,
                "documents/42/uuid-invoice.pdf",
                "invoice.pdf",
                "application/pdf",
                uploadedAt
        );
        byte[] pdfBytes = "%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8);

        when(storageService.download("documents/42/uuid-invoice.pdf")).thenReturn(pdfBytes);
        when(ocrService.extractText(pdfBytes)).thenReturn("Rechnung Nr. 2026-001");

        listener.handleOcrMessage(message);

        verify(storageService).download("documents/42/uuid-invoice.pdf");
        verify(ocrService).extractText(pdfBytes);

        // Verify strict order: IN_PROGRESS -> publishIndexingMessage -> DONE
        InOrder inOrder = inOrder(statusPublisher, indexingMessagePublisher);
        inOrder.verify(statusPublisher).publishInProgress(42L);
        inOrder.verify(indexingMessagePublisher).publishIndexingMessage(any(IndexingMessage.class));
        inOrder.verify(statusPublisher).publishDone(42L);

        ArgumentCaptor<IndexingMessage> indexingCaptor = ArgumentCaptor.forClass(IndexingMessage.class);
        verify(indexingMessagePublisher).publishIndexingMessage(indexingCaptor.capture());

        IndexingMessage published = indexingCaptor.getValue();
        assertThat(published.getDocumentId()).isEqualTo(42L);
        assertThat(published.getStorageKey()).isEqualTo("documents/42/uuid-invoice.pdf");
        assertThat(published.getFilename()).isEqualTo("invoice.pdf");
        assertThat(published.getExtractedText()).isEqualTo("Rechnung Nr. 2026-001");
        assertThat(published.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(published.getProcessedAt()).isNotNull();

        ArgumentCaptor<DocumentStatusEvent> statusCaptor = ArgumentCaptor.forClass(DocumentStatusEvent.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                eq("document.status.exchange"),
                eq("document.status.routingkey"),
                statusCaptor.capture()
        );

        List<DocumentStatusEvent> events = statusCaptor.getAllValues();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getDocumentId()).isEqualTo(42L);
        assertThat(events.get(0).getStage()).isEqualTo(ProcessingStage.OCR);
        assertThat(events.get(0).getStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS);

        assertThat(events.get(1).getDocumentId()).isEqualTo(42L);
        assertThat(events.get(1).getStage()).isEqualTo(ProcessingStage.OCR);
        assertThat(events.get(1).getStatus()).isEqualTo(ProcessingStatus.DONE);
        assertThat(events.get(1).getErrorMessage()).isNull();
        assertThat(events.get(1).getTimestamp()).isNotNull();

        verify(indexingMessagePublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void handleOcrMessage_whenPublishingIndexingMessageFails_doesNotPublishDoneStatus() {
        OcrMessage message = new OcrMessage(
                43L,
                "documents/43/invoice.pdf",
                "invoice.pdf",
                "application/pdf",
                LocalDateTime.now()
        );
        byte[] pdfBytes = "%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8);

        when(storageService.download("documents/43/invoice.pdf")).thenReturn(pdfBytes);
        when(ocrService.extractText(pdfBytes)).thenReturn("Text");
        doThrow(new AmqpException("Broker unreachable"))
                .when(indexingMessagePublisher).publishIndexingMessage(any(IndexingMessage.class));

        listener.handleOcrMessage(message);

        verify(statusPublisher, never()).publishDone(anyLong());
        verify(statusPublisher).publishFailed(eq(43L), contains("Broker unreachable"));
    }

    @Test
    void handleOcrMessage_missingObjectInRustFs_publishesFailedStatusEventWithoutRetry() {
        OcrMessage message = new OcrMessage(
                77L,
                "documents/77/missing.pdf",
                "missing.pdf",
                "application/pdf",
                LocalDateTime.now()
        );

        when(storageService.download("documents/77/missing.pdf"))
                .thenThrow(new StorageObjectNotFoundException("Object not found in RustFS"));

        listener.handleOcrMessage(message);

        // Only 1 attempt (no retries for permanent errors)
        verify(storageService, times(1)).download("documents/77/missing.pdf");
        verify(ocrService, never()).extractText(any());
        verify(statusPublisher, never()).publishDone(anyLong());

        ArgumentCaptor<DocumentStatusEvent> statusCaptor = ArgumentCaptor.forClass(DocumentStatusEvent.class);
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(
                eq("document.status.exchange"),
                eq("document.status.routingkey"),
                statusCaptor.capture()
        );

        DocumentStatusEvent lastEvent = statusCaptor.getAllValues().getLast();
        assertThat(lastEvent.getDocumentId()).isEqualTo(77L);
        assertThat(lastEvent.getStage()).isEqualTo(ProcessingStage.OCR);
        assertThat(lastEvent.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(lastEvent.getErrorMessage()).contains("Object not found in RustFS");

        verify(indexingMessagePublisher, never()).publishIndexingMessage(any());
        verify(indexingMessagePublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void handleOcrMessage_permanentOcrError_publishesFailedStatusEventWithoutRetry() {
        OcrMessage message = new OcrMessage(
                88L,
                "documents/88/corrupt.pdf",
                "corrupt.pdf",
                "application/pdf",
                LocalDateTime.now()
        );
        byte[] badPdf = "%PDF-corrupt".getBytes(StandardCharsets.UTF_8);

        when(storageService.download("documents/88/corrupt.pdf")).thenReturn(badPdf);
        when(ocrService.extractText(badPdf))
                .thenThrow(new OcrPermanentException("Ghostscript failed: password-protected or corrupt PDF"));

        listener.handleOcrMessage(message);

        verify(ocrService, times(1)).extractText(badPdf);
        verify(statusPublisher).publishFailed(88L, "Ghostscript failed: password-protected or corrupt PDF");
        verify(indexingMessagePublisher, never()).publishIndexingMessage(any());
        verify(indexingMessagePublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void handleOcrMessage_transientOcrError_retriesAndPublishesFailedStatusAndForwardsToDlq() {
        OcrMessage message = new OcrMessage(
                99L,
                "documents/99/timeout.pdf",
                "timeout.pdf",
                "application/pdf",
                LocalDateTime.now()
        );
        byte[] pdfBytes = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);

        when(storageService.download("documents/99/timeout.pdf")).thenReturn(pdfBytes);
        when(ocrService.extractText(pdfBytes))
                .thenThrow(new OcrTransientException("Tesseract timed out on page 1"));

        listener.handleOcrMessage(message);

        verify(ocrService, times(3)).extractText(pdfBytes);
        verify(statusPublisher).publishFailed(99L, "Tesseract timed out on page 1");
        verify(indexingMessagePublisher).publishToDeadLetterQueue(message);
        verify(indexingMessagePublisher, never()).publishIndexingMessage(any());
    }
}
