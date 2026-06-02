package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonDiveraRicRepository extends JpaRepository<PersonDiveraRic, Long> {

    List<PersonDiveraRic> findByPersonIdOrderByRicCodeAsc(long personId);

    void deleteByPersonId(long personId);
}
