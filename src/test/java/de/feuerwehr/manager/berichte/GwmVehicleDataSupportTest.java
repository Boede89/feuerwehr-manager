package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GwmVehicleDataSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseMergesEquipmentFromNonSelectedVehicles() {
        String vehiclesJson =
                "[{\"vehicleId\":1,\"maschinistPersonId\":10,\"einheitsfuehrerPersonId\":null,"
                        + "\"equipmentIds\":[100],\"defectiveEquipmentIds\":[],\"defectiveMangelByEquipmentId\":{}}]";
        String deployedJson =
                "[{\"vehicleId\":1,\"equipmentIds\":[100]},"
                        + "{\"vehicleId\":2,\"equipmentIds\":[200],\"customEquipment\":[{\"name\":\"Schlauchbrücke\",\"categoryName\":null}]}]";

        List<GwmVehicleData> vehicles = GwmVehicleDataSupport.parse(vehiclesJson, deployedJson, mapper);

        assertThat(vehicles).hasSize(2);
        GwmVehicleData first = vehicles.stream().filter(v -> v.vehicleId() == 1).findFirst().orElseThrow();
        assertThat(first.equipmentIds()).containsExactly(100L);
        assertThat(first.maschinistPersonId()).isEqualTo(10L);

        GwmVehicleData second = vehicles.stream().filter(v -> v.vehicleId() == 2).findFirst().orElseThrow();
        assertThat(second.equipmentIds()).containsExactly(200L);
        assertThat(second.customEquipment()).hasSize(1);
        assertThat(second.customEquipment().get(0).name()).isEqualTo("Schlauchbrücke");
    }

    @Test
    void toDeployedEquipmentJsonKeepsCustomEquipment() {
        List<GwmVehicleData> vehicles = List.of(new GwmVehicleData(
                5L,
                null,
                null,
                List.of(7L),
                List.of(),
                Map.of(),
                null,
                null,
                List.of(new CustomDeployedEquipment("Handscheinwerfer", "Beleuchtung"))));

        String json = GwmVehicleDataSupport.toDeployedEquipmentJson(vehicles, mapper);

        assertThat(json).contains("\"vehicleId\":5");
        assertThat(json).contains("7");
        assertThat(json).contains("Handscheinwerfer");
        assertThat(json).contains("Beleuchtung");
    }
}
