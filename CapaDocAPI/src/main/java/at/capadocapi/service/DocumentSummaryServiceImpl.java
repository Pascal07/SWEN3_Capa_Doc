package at.capadocapi.service;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSummaryServiceImpl implements DocumentSummaryService {

    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public void saveSummary(Long documentId, String summary) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("documentId must be a positive number");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be null or blank");
        }

        Optional<DocumentEntity> documentOpt = documentRepository.findById(documentId);
        if (documentOpt.isEmpty()) {
            log.warn("Received GenAI summary for non-existent or deleted documentId={}; acknowledging without update.",
                    documentId);
            return;
        }

        DocumentEntity document = documentOpt.get();
        String trimmedSummary = summary.trim();
        document.setSummary(trimmedSummary);
        document.setSummaryStatus(ProcessingStatus.DONE);
        documentRepository.save(document);

        log.info("Saved GenAI summary for documentId={} (summaryLength={})",
                documentId, trimmedSummary.length());
    }
}
