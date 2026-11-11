package at.capadocapi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Aktualisiert bestehende PostgreSQL-Check-Constraints der Tabelle {@code documents},
 * damit auch der Status {@code IN_PROGRESS} neben {@code PENDING}, {@code DONE} und {@code FAILED}
 * auf bereits angelegten Datenbank-Volumes akzeptiert wird.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSchemaUpdater {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureStatusCheckConstraintsAllowInProgress() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName == null || !productName.toLowerCase().contains("postgresql")) {
                return;
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_ocr_status_check");
                stmt.execute("ALTER TABLE documents ADD CONSTRAINT documents_ocr_status_check " +
                        "CHECK (ocr_status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED'))");
                stmt.execute("ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_index_status_check");
                stmt.execute("ALTER TABLE documents ADD CONSTRAINT documents_index_status_check " +
                        "CHECK (index_status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED'))");
                log.debug("Ensured PostgreSQL check constraints on documents allow IN_PROGRESS.");
            }
        } catch (Exception ex) {
            log.debug("Skipping PostgreSQL check constraint update: {}", ex.getMessage());
        }
    }
}
