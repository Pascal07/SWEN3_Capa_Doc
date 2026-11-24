package capa.genaiworker.service;

import capa.genaiworker.exception.InvalidOcrTextException;
import capa.genaiworker.exception.OllamaUnavailableException;
import capa.genaiworker.model.GenAiMessage;
import capa.genaiworker.model.SummaryResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenAiMessageListenerTest {

    @Mock
    private SummaryService summaryService;

    @Mock
    private SummaryResultPublisher summaryResultPublisher;

    @Mock
    private StatusPublisher statusPublisher;

    private GenAiMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new GenAiMessageListener(summaryService, summaryResultPublisher, statusPublisher);
    }

    @Test
    void handleGenAiMessage_validMessage_generatesAndPublishesSummary() {
        GenAiMessage message = GenAiMessage.builder()
                .documentId(7L)
                .filename("contract.pdf")
                .extractedText("Vertrag zwischen Partei A und Partei B.")
                .build();

        when(summaryService.generateSummary(7L, "Vertrag zwischen Partei A und Partei B."))
                .thenReturn("Dies ist ein Vertrag zwischen Partei A und Partei B.");

        listener.handleGenAiMessage(message);

        ArgumentCaptor<SummaryResultMessage> captor = ArgumentCaptor.forClass(SummaryResultMessage.class);
        verify(summaryResultPublisher).publishSummaryResult(captor.capture());
        assertThat(captor.getValue().getDocumentId()).isEqualTo(7L);
        assertThat(captor.getValue().getSummary()).isEqualTo("Dies ist ein Vertrag zwischen Partei A und Partei B.");
        verify(statusPublisher).publishInProgress(7L);
        verify(statusPublisher).publishDone(7L);
    }

    @Test
    void handleGenAiMessage_invalidDocumentId_rejectsWithoutRequeue() {
        GenAiMessage invalid = GenAiMessage.builder()
                .documentId(null)
                .extractedText("Text")
                .build();

        assertThatThrownBy(() -> listener.handleGenAiMessage(invalid))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(summaryService, never()).generateSummary(any(), any());
        verify(summaryResultPublisher, never()).publishSummaryResult(any());
    }

    @Test
    void handleGenAiMessage_emptyOcrText_rejectsWithoutRequeueForDlq() {
        GenAiMessage message = GenAiMessage.builder()
                .documentId(8L)
                .filename("empty.pdf")
                .extractedText("   ")
                .build();

        when(summaryService.generateSummary(8L, "   "))
                .thenThrow(new InvalidOcrTextException("Extracted OCR text is empty"));

        assertThatThrownBy(() -> listener.handleGenAiMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(InvalidOcrTextException.class);

        verify(summaryResultPublisher, never()).publishSummaryResult(any());
    }

    @Test
    void handleGenAiMessage_transientOllamaError_propagatesForSpringAmqpRetry() {
        GenAiMessage message = GenAiMessage.builder()
                .documentId(9L)
                .filename("report.pdf")
                .extractedText("Wichtiger Bericht.")
                .build();

        when(summaryService.generateSummary(9L, "Wichtiger Bericht."))
                .thenThrow(new OllamaUnavailableException("Connection refused"));

        assertThatThrownBy(() -> listener.handleGenAiMessage(message))
                .isInstanceOf(OllamaUnavailableException.class);

        verify(summaryResultPublisher, never()).publishSummaryResult(any());
    }
}
