package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonCourseCompletionRepository extends JpaRepository<PersonCourseCompletion, Long> {

    @Query("""
            SELECT c FROM PersonCourseCompletion c
            JOIN FETCH c.course course
            LEFT JOIN FETCH course.qualificationType
            WHERE c.person.id = :personId
            ORDER BY course.name
            """)
    List<PersonCourseCompletion> findByPersonId(@Param("personId") long personId);

    boolean existsByPersonIdAndCourseId(long personId, long courseId);

    @Query("SELECT DISTINCT c.person.id FROM PersonCourseCompletion c WHERE c.course.id = :courseId")
    List<Long> findPersonIdsByCourseId(@Param("courseId") long courseId);

    void deleteByPersonId(long personId);
}
