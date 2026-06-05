package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonEquipmentRepository extends JpaRepository<PersonEquipment, Long> {

    List<PersonEquipment> findByPersonIdOrderByCreatedAtDesc(long personId);

    boolean existsByIdAndPersonId(long id, long personId);
}
