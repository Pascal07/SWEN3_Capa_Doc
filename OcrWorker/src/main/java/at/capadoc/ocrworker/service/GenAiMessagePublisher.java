package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.GenAiMessage;

public interface GenAiMessagePublisher {

    void publishGenAiMessage(GenAiMessage message);
}
