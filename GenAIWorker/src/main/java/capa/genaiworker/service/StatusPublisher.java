package capa.genaiworker.service;

public interface StatusPublisher {

    void publishInProgress(Long documentId);

    void publishDone(Long documentId);

    void publishFailed(Long documentId, String errorMessage);
}
