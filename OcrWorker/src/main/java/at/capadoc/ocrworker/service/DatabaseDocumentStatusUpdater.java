package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.ProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseDocumentStatusUpdater implements DocumentStatusUpdater {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<ProcessingStatus> getOcrStatus(Long documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        try {
            List<String> results = jdbcTemplate.query(
                    "SELECT ocr_status FROM documents WHERE id = ?",
                    (rs, rowNum) -> rs.getString("ocr_status"),
                    documentId
            );
            if (results.isEmpty() || results.getFirst() == null) {
                return Optional.empty();
            }
            return Optional.of(ProcessingStatus.valueOf(results.getFirst()));
        } catch (DataAccessException | IllegalArgumentException ex) {
            log.warn("Could not read current ocr_status for documentId={}: {}", documentId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void updateOcrStatus(Long documentId, ProcessingStatus status) {
        if (documentId == null || status == null) {
            return;
        }
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE documents SET ocr_status = ? WHERE id = ?",
                    status.name(),
                    documentId
            );
            log.info("Updated ocr_status={} for documentId={} (rows affected={})",
                    status, documentId, updated);
        } catch (DataAccessException ex) {
            log.error("Failed to update ocr_status={} for documentId={}", status, documentId, ex);
        }
    }
}
