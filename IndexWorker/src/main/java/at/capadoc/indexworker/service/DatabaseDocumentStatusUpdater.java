package at.capadoc.indexworker.service;

import at.capadoc.indexworker.model.ProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseDocumentStatusUpdater implements DocumentStatusUpdater {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<ProcessingStatus> getIndexStatus(Long documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        try {
            List<String> results = jdbcTemplate.query(
                    "SELECT index_status FROM documents WHERE id = ?",
                    (rs, rowNum) -> rs.getString("index_status"),
                    documentId
            );
            if (results.isEmpty() || results.getFirst() == null) {
                return Optional.empty();
            }
            return Optional.of(ProcessingStatus.valueOf(results.getFirst()));
        } catch (DataAccessException | IllegalArgumentException ex) {
            log.warn("Could not read current index_status for documentId={}: {}", documentId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void updateIndexStatus(Long documentId, ProcessingStatus status) {
        if (documentId == null || status == null) {
            return;
        }
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE documents SET index_status = ? WHERE id = ?",
                    status.name(),
                    documentId
            );
            log.info("Updated index_status={} for documentId={} (rows affected={})",
                    status, documentId, updated);
        } catch (DataAccessException ex) {
            log.error("Failed to update index_status={} for documentId={}", status, documentId, ex);
        }
    }

    @Override
    public Optional<LocalDateTime> getUploadedAt(Long documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        try {
            List<LocalDateTime> results = jdbcTemplate.query(
                    "SELECT uploaded_at FROM documents WHERE id = ?",
                    (rs, rowNum) -> {
                        Timestamp ts = rs.getTimestamp("uploaded_at");
                        return ts != null ? ts.toLocalDateTime() : null;
                    },
                    documentId
            );
            if (results.isEmpty() || results.getFirst() == null) {
                return Optional.empty();
            }
            return Optional.of(results.getFirst());
        } catch (DataAccessException ex) {
            log.warn("Could not read uploaded_at for documentId={}: {}", documentId, ex.getMessage());
            return Optional.empty();
        }
    }
}
