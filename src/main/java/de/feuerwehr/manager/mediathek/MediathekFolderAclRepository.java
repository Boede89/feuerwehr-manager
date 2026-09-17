package de.feuerwehr.manager.mediathek;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediathekFolderAclRepository extends JpaRepository<MediathekFolderAcl, Long> {

    List<MediathekFolderAcl> findByFolderId(long folderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MediathekFolderAcl a WHERE a.folder.id = :folderId")
    void deleteByFolderId(@Param("folderId") long folderId);
}
