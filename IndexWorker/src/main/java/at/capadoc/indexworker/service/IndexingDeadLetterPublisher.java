package at.capadoc.indexworker.service;

import at.capadoc.indexworker.model.IndexingMessage;

public interface IndexingDeadLetterPublisher {

    void publishToDeadLetterQueue(IndexingMessage failedMessage);
}
