-- Fahrzeug am Einsatz beteiligt (unabhängig von Besatzung)

ALTER TABLE incident_report_vehicles
    ADD COLUMN involved BOOLEAN NOT NULL DEFAULT FALSE AFTER vehicle_name;

UPDATE incident_report_vehicles irv
SET involved = TRUE
WHERE irv.vehicle_id IS NOT NULL
  AND EXISTS (
    SELECT 1
    FROM incident_report_personnel irp
    WHERE irp.incident_report_vehicle_id = irv.id
);
