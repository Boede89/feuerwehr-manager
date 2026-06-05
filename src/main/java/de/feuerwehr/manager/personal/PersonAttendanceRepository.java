package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonAttendanceRepository extends JpaRepository<PersonAttendance, Long> {

    List<PersonAttendance> findByPersonIdOrderByServiceDateDesc(long personId);

    boolean existsByIdAndPersonId(long id, long personId);
}
