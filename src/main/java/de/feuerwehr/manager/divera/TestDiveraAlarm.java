package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "test_divera_alarms")
public class TestDiveraAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "alarm_id", nullable = false)
    private long alarmId;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(nullable = false, length = 512)
    private String title = "";

    @Column(name = "alarm_text", columnDefinition = "TEXT")
    private String alarmText;

    @Column(length = 512)
    private String address;

    @Column(name = "date_epoch_seconds", nullable = false)
    private long dateEpochSeconds;

    @Column(name = "ts_create_seconds", nullable = false)
    private long tsCreateSeconds;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;
}
