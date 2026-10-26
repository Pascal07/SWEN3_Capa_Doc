package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.OcrMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;

class OcrMessageListenerTest {

    private final OcrMessageListener listener = new OcrMessageListener();

    @Test
    void handleOcrMessage_acceptsReceivedMessage() {
        OcrMessage message = new OcrMessage(
                12L,
                "report.pdf",
                "application/pdf",
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );

        assertThatCode(() -> listener.handleOcrMessage(message))
                .doesNotThrowAnyException();
    }
}

