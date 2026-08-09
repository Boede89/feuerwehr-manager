package de.feuerwehr.manager.reservierungen;

import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.unit.Unit;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicle_reservations")
public class VehicleReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /** Erstes / primäres Fahrzeug (für Index & Abwärtskompatibilität). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vehicle_reservation_vehicles",
            joinColumns = @JoinColumn(name = "reservation_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    @OrderColumn(name = "sort_order")
    private List<Vehicle> vehicles = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id")
    private User requesterUser;

    @Column(name = "requester_name", nullable = false, length = 255)
    private String requesterName;

    @Column(name = "requester_email", nullable = false, length = 255)
    private String requesterEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(length = 255)
    private String location;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "divera_event_id")
    private Long diveraEventId;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public void setVehiclesOrdered(List<Vehicle> ordered) {
        this.vehicles = ordered == null ? new ArrayList<>() : new ArrayList<>(ordered);
        if (!this.vehicles.isEmpty()) {
            this.vehicle = this.vehicles.get(0);
        }
    }

    public List<Vehicle> resolvedVehicles() {
        if (vehicles != null && !vehicles.isEmpty()) {
            return List.copyOf(vehicles);
        }
        return vehicle != null ? List.of(vehicle) : List.of();
    }

    public String vehicleNamesJoined() {
        return resolvedVehicles().stream().map(Vehicle::getName).collect(Collectors.joining(", "));
    }
}
