package de.feuerwehr.manager.atemschutz;

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
@Table(name = "atemschutz_reminder_log")
public class AtemschutzReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrier_id", nullable = false)
    private AtemschutzCarrier carrier;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_type", nullable = false, length = 32)
    private AtemschutzFitnessType fitnessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mail_kind", nullable = false, length = 16)
    private AtemschutzReminderMailKind mailKind;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "sent_at", nullable = false, insertable = false, updatable = false)
    private Instant sentAt;
}
