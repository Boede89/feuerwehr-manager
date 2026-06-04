package de.feuerwehr.manager.unit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "units")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String street;

    @Column(name = "postal_city", length = 255)
    private String postalCity;

    @Column(name = "logo_base64", columnDefinition = "MEDIUMTEXT")
    private String logoBase64;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    /** Aktivierte Navigations-Module für diese Einheit (JSON). */
    @Column(name = "modules_json")
    private String modulesJson;
}
