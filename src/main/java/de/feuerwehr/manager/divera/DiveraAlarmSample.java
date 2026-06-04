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
@Table(name = "divera_alarm_samples")
public class DiveraAlarmSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "alarm_id", nullable = false)
    private long alarmId;

    @Column(nullable = false, length = 512)
    private String title = "";

    @Column(length = 512)
    private String address;

    @Column(name = "webhook_payload", nullable = false, columnDefinition = "TEXT")
    private String webhookPayload;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();
}
