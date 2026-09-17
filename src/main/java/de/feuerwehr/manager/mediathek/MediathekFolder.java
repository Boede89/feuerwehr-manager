package de.feuerwehr.manager.mediathek;

import de.feuerwehr.manager.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mediathek_folders")
public class MediathekFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MediathekFolder parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_unit_id", nullable = false)
    private Unit ownerUnit;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "inherit_acl", nullable = false)
    private boolean inheritAcl = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "mediathek_folder_units",
            joinColumns = @JoinColumn(name = "folder_id"),
            inverseJoinColumns = @JoinColumn(name = "unit_id"))
    private Set<Unit> sharedUnits = new LinkedHashSet<>();

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, name ASC")
    private List<MediathekFolder> children = new ArrayList<>();

    @OneToMany(mappedBy = "folder", fetch = FetchType.LAZY)
    @OrderBy("originalName ASC")
    private List<MediathekFile> files = new ArrayList<>();

    @OneToMany(mappedBy = "folder", fetch = FetchType.LAZY, orphanRemoval = true)
    private List<MediathekFolderAcl> aclEntries = new ArrayList<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public boolean isSharedWith(long unitId) {
        if (ownerUnit != null && ownerUnit.getId().equals(unitId)) {
            return true;
        }
        return sharedUnits != null && sharedUnits.stream().anyMatch(u -> u.getId().equals(unitId));
    }
}
