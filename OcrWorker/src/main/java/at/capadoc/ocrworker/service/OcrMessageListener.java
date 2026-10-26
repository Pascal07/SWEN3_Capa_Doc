package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.OcrMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class OcrMessageListener {

    private static final Logger log =
            LoggerFactory.getLogger(OcrMessageListener.class);

    @RabbitListener(queues = "${capadoc.rabbitmq.queue}")
    public void handleOcrMessage(OcrMessage message) {
        log.info(
                "Received OCR message: documentId={}, filename={}",
                message.getDocumentId(),
                message.getFilename()
        );

        // Platzhalter für Sprint 4:
        log.info(
                "OCR processing placeholder for documentId={}, filename={}",
                message.getDocumentId(),
                message.getFilename()
        );
    }
}