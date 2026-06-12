package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;

import de.feuerwehr.manager.unit.Unit;
import org.junit.jupiter.api.Test;

class UnitAddressSupportTest {

    @Test
    void fromUnit_parsesPostalCityAndStreet() {
        Unit unit = new Unit();
        unit.setName("FF Musterstadt");
        unit.setPostalCity("12345 Musterstadt");
        unit.setStreet("Hauptstraße 12");

        UnitAddressSupport.UnitAddress address = UnitAddressSupport.fromUnit(unit);

        assertThat(address.location()).isEqualTo("Musterstadt");
        assertThat(address.postalCode()).isEqualTo("12345");
        assertThat(address.street()).isEqualTo("Hauptstraße");
        assertThat(address.houseNumber()).isEqualTo("12");
    }

    @Test
    void parseStreet_keepsUnsplittableLineInStreetField() {
        UnitAddressSupport.StreetParts parts = UnitAddressSupport.parseStreet("Feuerwehrhaus");

        assertThat(parts.street()).isEqualTo("Feuerwehrhaus");
        assertThat(parts.houseNumber()).isNull();
    }
}
