package de.feuerwehr.manager.mediathek;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediathekFileRepository extends JpaRepository<MediathekFile, Long> {

    List<MediathekFile> findByFolderIdOrderByOriginalNameAsc(long folderId);

    @Query(
            """
            SELECT f FROM MediathekFile f
            JOIN FETCH f.folder folder
            LEFT JOIN FETCH folder.ownerUnit
            LEFT JOIN FETCH folder.sharedUnits
            WHERE f.id = :id
            """)
    Optional<MediathekFile> findByIdWithFolder(@Param("id") long id);
}
