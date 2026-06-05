package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonQualificationRepository extends JpaRepository<PersonQualification, Long> {

    List<PersonQualification> findByPersonIdOrderByNameAsc(long personId);

    boolean existsByIdAndPersonId(long id, long personId);
}
