package de.feuerwehr.manager.mediathek;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediathekFolderRepository extends JpaRepository<MediathekFolder, Long> {

    @Query(
            """
            SELECT DISTINCT f FROM MediathekFolder f
            LEFT JOIN FETCH f.sharedUnits
            LEFT JOIN FETCH f.ownerUnit
            WHERE f.parent IS NULL
              AND (f.ownerUnit.id = :unitId OR EXISTS (
                    SELECT 1 FROM f.sharedUnits su WHERE su.id = :unitId
                  ))
            ORDER BY f.sortOrder ASC, f.name ASC
            """)
    List<MediathekFolder> findRootFoldersVisibleInUnit(@Param("unitId") long unitId);

    @Query(
            """
            SELECT DISTINCT f FROM MediathekFolder f
            LEFT JOIN FETCH f.sharedUnits
            LEFT JOIN FETCH f.ownerUnit
            WHERE f.parent.id = :parentId
              AND (f.ownerUnit.id = :unitId OR EXISTS (
                    SELECT 1 FROM f.sharedUnits su WHERE su.id = :unitId
                  ))
            ORDER BY f.sortOrder ASC, f.name ASC
            """)
    List<MediathekFolder> findChildrenVisibleInUnit(
            @Param("parentId") long parentId, @Param("unitId") long unitId);

    @Query(
            """
            SELECT f FROM MediathekFolder f
            LEFT JOIN FETCH f.sharedUnits
            LEFT JOIN FETCH f.ownerUnit
            LEFT JOIN FETCH f.parent
            WHERE f.id = :id
            """)
    Optional<MediathekFolder> findByIdWithUnits(@Param("id") long id);

    @Query(
            """
            SELECT f FROM MediathekFolder f
            LEFT JOIN FETCH f.aclEntries a
            LEFT JOIN FETCH a.person
            LEFT JOIN FETCH a.group
            LEFT JOIN FETCH a.qualificationType qt
            LEFT JOIN FETCH qt.unit
            WHERE f.id = :id
            """)
    Optional<MediathekFolder> findByIdWithAcl(@Param("id") long id);

    @Query("SELECT f FROM MediathekFolder f WHERE f.parent.id = :parentId")
    List<MediathekFolder> findByParentId(@Param("parentId") long parentId);
}
