package de.feuerwehr.manager.technik;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "vehicle_type", length = 32)
    private String vehicleType = "lkw";

    @Column(name = "license_plate", length = 20)
    private String licensePlate;

    @Column(name = "year_built")
    private Integer yearBuilt;

    @Column(length = 50)
    private String phone;

    @Column(name = "length_m", precision = 8, scale = 2)
    private BigDecimal lengthM;

    @Column(name = "width_m", precision = 8, scale = 2)
    private BigDecimal widthM;

    @Column(name = "height_m", precision = 8, scale = 2)
    private BigDecimal heightM;

    @Column(name = "weight_kg")
    private Integer weightKg;

    @Column(name = "service_status", nullable = false, length = 32)
    private String serviceStatus = "aktiv";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
