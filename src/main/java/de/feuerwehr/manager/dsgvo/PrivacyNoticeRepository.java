package de.feuerwehr.manager.dsgvo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyNoticeRepository extends JpaRepository<PrivacyNotice, Long> {

    Optional<PrivacyNotice> findTopByOrderByActiveFromDesc();
}
