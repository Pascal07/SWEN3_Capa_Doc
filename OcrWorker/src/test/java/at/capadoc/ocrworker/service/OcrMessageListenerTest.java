package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.OcrProperties;
import at.capadoc.ocrworker.exception.OcrPermanentException;
import at.capadoc.ocrworker.exception.OcrTransientException;
import at.capadoc.ocrworker.exception.StorageObjectNotFoundException;
import at.capadoc.ocrworker.model.IndexingMessage;
import at.capadoc.ocrworker.model.OcrMessage;
import at.capadoc.ocrworker.model.ProcessingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

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
    private DocumentStatusUpdater documentStatusUpdater;

    private OcrProperties ocrProperties;
    private OcrMessageListener listener;

    @BeforeEach
    void setUp() {
        ocrProperties = new OcrProperties();
        ocrProperties.setMaxRetries(3);
        ocrProperties.setRetryBackoffMs(5L); // fast backoff for unit tests

        listener = new OcrMessageListener(
                storageService,
                ocrService,
                indexingMessagePublisher,
                documentStatusUpdater,
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
    void handleOcrMessage_successFlow_downloadsExtractsUpdatesStatusAndPublishesIndexingMessage() {
        OcrMessage message = new OcrMessage(
                42L,
                "documents/42/uuid-invoice.pdf",
                "invoice.pdf",
                "application/pdf",
                LocalDateTime.of(2026, 5, 1, 12, 0)
        );
        byte[] pdfBytes = "%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8);

        when(documentStatusUpdater.getOcrStatus(42L)).thenReturn(Optional.of(ProcessingStatus.PENDING));
        when(storageService.download("documents/42/uuid-invoice.pdf")).thenReturn(pdfBytes);
        when(ocrService.extractText(pdfBytes)).thenReturn("Rechnung Nr. 2026-001");

        listener.handleOcrMessage(message);

        verify(storageService).download("documents/42/uuid-invoice.pdf");
        verify(ocrService).extractText(pdfBytes);
        verify(documentStatusUpdater).updateOcrStatus(42L, ProcessingStatus.DONE);

        ArgumentCaptor<IndexingMessage> indexingCaptor = ArgumentCaptor.forClass(IndexingMessage.class);
        verify(indexingMessagePublisher).publishIndexingMessage(indexingCaptor.capture());

        IndexingMessage published = indexingCaptor.getValue();
        assertThat(published.getDocumentId()).isEqualTo(42L);
        assertThat(published.getStorageKey()).isEqualTo("documents/42/uuid-invoice.pdf");
        assertThat(published.getFilename()).isEqualTo("invoice.pdf");
        assertThat(published.getExtractedText()).isEqualTo("Rechnung Nr. 2026-001");
        assertThat(published.getProcessedAt()).isNotNull();

        verify(indexingMessagePublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void handleOcrMessage_missingObjectInRustFs_setsFailedStatusWithoutRetry() {
        OcrMessage message = new OcrMessage(
                77L,
                "documents/77/missing.pdf",
                "missing.pdf",
                "application/pdf",
                LocalDateTime.now()
        );

        when(documentStatusUpdater.getOcrStatus(77L)).thenReturn(Optional.of(ProcessingStatus.PENDING));
        when(storageService.download("documents/77/missing.pdf"))
                .thenThrow(new StorageObjectNotFoundException("Object not found in RustFS"));

        listener.handleOcrMessage(message);

        // Only 1 attempt (no retries for permanent errors)
        verify(storageService, times(1)).download("documents/77/missing.pdf");
        verify(ocrService, never()).extractText(any());
        verify(documentStatusUpdater).updateOcrStatus(77L, ProcessingStatus.FAILED);
        verify(indexingMessagePublisher, never()).publishIndexingMessage(any());
        verify(indexingMessagePublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void handleOcrMessage_permanentOcrError_setsFailedStatusWithoutRetry() {
        OcrMessage message = new OcrMessage(
                88L,
                "documents/88/corrupt.pdf",
                "corrupt.pdf",
                "application/pdf",
                LocalDateTime.now()
        );
        byte[] badPdf = "%PDF-corrupt".getBytes(StandardCharsets.UTF_8);

        when(documentStatusUpdater.getOcrStatus(88L)).thenReturn(Optional.of(ProcessingStatus.PENDING));
        when(storageService.download("documents/88/corrupt.pdf")).thenReturn(badPdf);
        when(ocrService.extractText(badPdf))
                .thenThrow(new OcrPermanentException("Ghostscript failed: password-protected or corrupt PDF"));

        listener.handleOcrMessage(message);

        verify(ocrService, times(1)).extractText(badPdf);
        verify(documentStatusUpdater).updateOcrStatus(88L, ProcessingStatus.FAILED);
        verify(indexingMessagePublisher, never()).publishIndexingMessage(any());
        verify(indexingMessagePublisher, never()).publishToDeadLetterQueue(any());
    }

    @Test
    void handleOcrMessage_transientOcrError_retriesAndForwardsToDlqAfterExhaustion() {
        OcrMessage message = new OcrMessage(
                99L,
                "documents/99/timeout.pdf",
                "timeout.pdf",
                "application/pdf",
                LocalDateTime.now()
        );
        byte[] pdfBytes = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);

        when(documentStatusUpdater.getOcrStatus(99L)).thenReturn(Optional.of(ProcessingStatus.PENDING));
        when(storageService.download("documents/99/timeout.pdf")).thenReturn(pdfBytes);
        when(ocrService.extractText(pdfBytes))
                .thenThrow(new OcrTransientException("Tesseract timed out on page 1"));

        listener.handleOcrMessage(message);

        verify(ocrService, times(3)).extractText(pdfBytes);
        verify(documentStatusUpdater).updateOcrStatus(99L, ProcessingStatus.FAILED);
        verify(indexingMessagePublisher).publishToDeadLetterQueue(message);
        verify(indexingMessagePublisher, never()).publishIndexingMessage(any());
    }

    @Test
    void handleOcrMessage_alreadyDoneDocument_isIdempotentAndSkipsProcessing() {
        OcrMessage message = new OcrMessage(
                55L,
                "documents/55/already-done.pdf",
                "already-done.pdf",
                "application/pdf",
                LocalDateTime.now()
        );

        when(documentStatusUpdater.getOcrStatus(55L)).thenReturn(Optional.of(ProcessingStatus.DONE));

        listener.handleOcrMessage(message);

        verify(storageService, never()).download(any());
        verify(ocrService, never()).extractText(any());
        verify(indexingMessagePublisher, never()).publishIndexingMessage(any());
    }
}
