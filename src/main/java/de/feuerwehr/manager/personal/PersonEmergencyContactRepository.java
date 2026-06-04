package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonEmergencyContactRepository extends JpaRepository<PersonEmergencyContact, Long> {

    List<PersonEmergencyContact> findByPersonIdOrderBySortOrderAscNameAsc(long personId);

    Optional<PersonEmergencyContact> findByIdAndPersonId(long id, long personId);
}
