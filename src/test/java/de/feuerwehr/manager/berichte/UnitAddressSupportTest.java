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
        assertThat(address.district()).isEqualTo("Musterstadt");
    }

    @Test
    void fromUnit_derivesDistrictFromPostalCityWithOrtsteil() {
        Unit unit = new Unit();
        unit.setName("Löschzug Amern");
        unit.setPostalCity("41366 Schwalmtal Amern");
        unit.setStreet("Kirchplatz 1");

        UnitAddressSupport.UnitAddress address = UnitAddressSupport.fromUnit(unit);

        assertThat(address.location()).isEqualTo("Schwalmtal Amern");
        assertThat(address.postalCode()).isEqualTo("41366");
        assertThat(address.district()).isEqualTo("Amern");
    }

    @Test
    void applyDefaultsToForm_fillsOnlyPostalCode() {
        Unit unit = new Unit();
        unit.setPostalCity("54321 Beispielstadt");
        unit.setStreet("Feuerwehrstraße 7");
        EinsatzberichtForm form = new EinsatzberichtForm();
        form.setLocation("—");

        UnitAddressSupport.applyDefaultsToForm(form, unit);

        assertThat(form.getPostalCode()).isEqualTo("54321");
        assertThat(form.getLocation()).isEqualTo("—");
        assertThat(form.getStreet()).isNull();
        assertThat(form.getHouseNumber()).isNull();
        assertThat(form.getObjekt()).isNull();
        assertThat(form.getDistrict()).isNull();
    }

    @Test
    void applyDefaultsToForm_doesNotOverwriteClearedAddressFields() {
        Unit unit = new Unit();
        unit.setPostalCity("54321 Beispielstadt");
        unit.setStreet("Feuerwehrstraße 7");
        EinsatzberichtForm form = new EinsatzberichtForm();
        form.setPostalCode("54321");
        form.setLocation("");
        form.setStreet("");
        form.setHouseNumber("");
        form.setObjekt("");

        UnitAddressSupport.applyDefaultsToFormIfBlank(form, unit);

        assertThat(form.getPostalCode()).isEqualTo("54321");
        assertThat(form.getLocation()).isEmpty();
        assertThat(form.getStreet()).isEmpty();
        assertThat(form.getHouseNumber()).isEmpty();
        assertThat(form.getObjekt()).isEmpty();
    }

    @Test
    void parseStreet_splitsHouseNumberWithLetterSuffix() {
        UnitAddressSupport.StreetParts parts = UnitAddressSupport.parseStreet("Geneschen 109 B");

        assertThat(parts.street()).isEqualTo("Geneschen");
        assertThat(parts.houseNumber()).isEqualTo("109 B");
    }

    @Test
    void parseStreet_splitsHouseNumberRange() {
        UnitAddressSupport.StreetParts parts = UnitAddressSupport.parseStreet("Hauptstraße 10-12");

        assertThat(parts.street()).isEqualTo("Hauptstraße");
        assertThat(parts.houseNumber()).isEqualTo("10-12");
    }

    @Test
    void parseStreet_keepsUnsplittableLineInStreetField() {
        UnitAddressSupport.StreetParts parts = UnitAddressSupport.parseStreet("Feuerwehrhaus");

        assertThat(parts.street()).isEqualTo("Feuerwehrhaus");
        assertThat(parts.houseNumber()).isNull();
    }
}
