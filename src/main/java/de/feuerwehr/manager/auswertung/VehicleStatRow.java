package de.feuerwehr.manager.auswertung;

public record VehicleStatRow(
        long vehicleId,
        String vehicleName,
        int einsaetze,
        int uebungen,
        int sonstiges,
        int gesamt,
        double durchschnittBesatzung,
        int alsMaschinistEinsaetze,
        int alsEfEinsaetze) {}
