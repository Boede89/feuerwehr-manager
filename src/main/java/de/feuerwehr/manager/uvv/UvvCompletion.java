package de.feuerwehr.manager.uvv;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.user.User;
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
@Table(name = "uvv_completions")
public class UvvCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private UvvCampaign campaign;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UvvCompletionChannel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedBy;

    @Column(name = "recorded_by_display_name", length = 255)
    private String recordedByDisplayName;

    @Column(name = "quiz_passed", nullable = false)
    private boolean quizPassed;

    @Column(length = 1024)
    private String notes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
