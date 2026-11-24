package capa.genaiworker.service;

import capa.genaiworker.model.SummaryResultMessage;

public interface SummaryResultPublisher {

    void publishSummaryResult(SummaryResultMessage message);
}
