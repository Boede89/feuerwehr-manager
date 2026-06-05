package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonHonorRepository extends JpaRepository<PersonHonor, Long> {

    List<PersonHonor> findByPersonIdOrderByAwardedAtDescNameAsc(long personId);

    boolean existsByIdAndPersonId(long id, long personId);
}
