package de.feuerwehr.manager.uvv;

import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.termine.UnitTermin;
import de.feuerwehr.manager.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "uvv_campaigns")
public class UvvCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "content_text", columnDefinition = "MEDIUMTEXT")
    private String contentText;

    @Column(name = "presentation_original_name", length = 255)
    private String presentationOriginalName;

    @Column(name = "presentation_stored_name", length = 255)
    private String presentationStoredName;

    @Column(name = "presentation_mime_type", length = 128)
    private String presentationMimeType;

    @Column(name = "presentation_page_count")
    private Integer presentationPageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UvvCampaignStatus status = UvvCampaignStatus.OPEN;

    public boolean hasPresentation() {
        return presentationStoredName != null
                && !presentationStoredName.isBlank()
                && presentationPageCount != null
                && presentationPageCount > 0;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_report_id")
    private AttendanceReport attendanceReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_termin_id")
    private UnitTermin unitTermin;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
