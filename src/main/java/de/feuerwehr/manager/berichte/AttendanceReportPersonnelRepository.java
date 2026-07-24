package de.feuerwehr.manager.berichte;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceReportPersonnelRepository extends JpaRepository<AttendanceReportPersonnel, Long> {

    @Query("""
            SELECT p FROM AttendanceReportPersonnel p
            LEFT JOIN FETCH p.person
            WHERE p.attendanceReport.id = :reportId
            ORDER BY p.sortOrder ASC, p.displayName ASC
            """)
    List<AttendanceReportPersonnel> findByReportId(@Param("reportId") long reportId);

    @Query("""
            SELECT p FROM AttendanceReportPersonnel p
            JOIN FETCH p.attendanceReport
            LEFT JOIN FETCH p.person pers
            LEFT JOIN FETCH pers.qualificationType
            WHERE p.attendanceReport.id IN :reportIds
            """)
    List<AttendanceReportPersonnel> findByReportIdIn(@Param("reportIds") Collection<Long> reportIds);

    @Modifying
    @Query("DELETE FROM AttendanceReportPersonnel p WHERE p.attendanceReport.id = :reportId")
    void deleteByReportId(@Param("reportId") long reportId);
}
