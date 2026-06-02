package de.feuerwehr.manager.user;

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
@Table(name = "user_rfid_cards")
public class UserRfidCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Normalisierte Chip-ID (Großbuchstaben, ohne Leerzeichen). */
    @Column(name = "card_uid", nullable = false, unique = true, length = 128)
    private String cardUid;

    @Column(length = 255)
    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "registered_at", nullable = false, insertable = false, updatable = false)
    private Instant registeredAt;
}
