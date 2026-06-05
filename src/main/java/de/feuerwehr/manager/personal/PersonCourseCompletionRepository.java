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

    @Query("""
            SELECT DISTINCT cc.person FROM PersonCourseCompletion cc
            JOIN cc.person p
            JOIN cc.course c
            WHERE p.unit.id = :unitId
              AND p.anonymizedAt IS NULL
              AND p.testData = :testData
              AND LOWER(TRIM(c.name)) = LOWER(TRIM(:courseName))
              AND (cc.completedOn IS NOT NULL OR cc.completionYear IS NOT NULL)
            ORDER BY p.lastName, p.firstName
            """)
    List<de.feuerwehr.manager.personal.Person> findPersonsWithCompletedCourse(
            @Param("unitId") long unitId,
            @Param("testData") boolean testData,
            @Param("courseName") String courseName);

    @Query("""
            SELECT DISTINCT cc.person FROM PersonCourseCompletion cc
            JOIN cc.person p
            JOIN cc.course c
            WHERE p.unit.id = :unitId
              AND p.anonymizedAt IS NULL
              AND p.testData = :testData
              AND (c.id = :courseId OR c.productionSourceId = :courseId)
              AND (cc.completedOn IS NOT NULL OR cc.completionYear IS NOT NULL)
            ORDER BY p.lastName, p.firstName
            """)
    List<de.feuerwehr.manager.personal.Person> findPersonsWithCompletedCourseId(
            @Param("unitId") long unitId,
            @Param("testData") boolean testData,
            @Param("courseId") long courseId);
}
