package de.feuerwehr.manager.unit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByActiveTrueOrderByNameAsc();

    List<Unit> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
