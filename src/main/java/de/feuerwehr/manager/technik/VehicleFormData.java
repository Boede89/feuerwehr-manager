package de.feuerwehr.manager.technik;

import java.math.BigDecimal;

public record VehicleFormData(
        String name,
        String description,
        String vehicleType,
        String licensePlate,
        Integer yearBuilt,
        String phone,
        BigDecimal lengthM,
        BigDecimal widthM,
        BigDecimal heightM,
        Integer weightKg,
        String serviceStatus,
        String notes) {}
