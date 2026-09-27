package at.capadocapi.repository;

import at.capadocapi.model.ShareLinkEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, Long> {
    Optional<ShareLinkEntity> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
}