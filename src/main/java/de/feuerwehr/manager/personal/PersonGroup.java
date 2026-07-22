package de.feuerwehr.manager.personal;

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
import jakarta.persistence.OrderBy;
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
@Table(name = "person_groups")
public class PersonGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "person_group_members",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    @OrderBy("lastName ASC, firstName ASC")
    private List<Person> members = new ArrayList<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public String memberIdsCsv() {
        if (members == null || members.isEmpty()) {
            return "";
        }
        return members.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.joining(","));
    }

    public String memberNamesLabel() {
        if (members == null || members.isEmpty()) {
            return "";
        }
        Long ownUnitId = unit != null ? unit.getId() : null;
        return members.stream()
                .map(person -> {
                    String name = person.displayName();
                    if (ownUnitId != null
                            && person.getUnit() != null
                            && !ownUnitId.equals(person.getUnit().getId())) {
                        return name + " (" + person.getUnit().getName() + ")";
                    }
                    return name;
                })
                .collect(Collectors.joining(", "));
    }

    /** JSON für JS: Mitglieder anderer Einheiten [{id,name,unitName}, …]. */
    public String foreignMembersJson() {
        if (members == null || members.isEmpty() || unit == null) {
            return "[]";
        }
        Long ownUnitId = unit.getId();
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Person person : members) {
            if (person.getUnit() == null || ownUnitId.equals(person.getUnit().getId())) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":")
                    .append(person.getId())
                    .append(",\"name\":\"")
                    .append(escapeJson(person.displayName()))
                    .append("\",\"unitName\":\"")
                    .append(escapeJson(person.getUnit().getName()))
                    .append("\"}");
        }
        json.append(']');
        return json.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
