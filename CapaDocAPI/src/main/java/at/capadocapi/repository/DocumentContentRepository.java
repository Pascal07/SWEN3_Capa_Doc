package at.capadocapi.repository;

import at.capadocapi.model.DocumentContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentContentRepository extends JpaRepository<DocumentContentEntity, Long> {
}
