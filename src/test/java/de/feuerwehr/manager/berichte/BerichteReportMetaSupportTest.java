package de.feuerwehr.manager.berichte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.feuerwehr.manager.user.User;
import org.junit.jupiter.api.Test;

class BerichteReportMetaSupportTest {

    @Test
    void manualReportUsesCreatedByUser() {
        User creator = new User();
        creator.setUsername("max");
        creator.setDisplayName("Max Mustermann");

        IncidentReport report = new IncidentReport();
        report.setCreatedByUser(creator);
        report.setCreatedByName("Max Mustermann");

        BerichteReportMetaSupport.MetaView meta = BerichteReportMetaSupport.forIncident(report);

        assertNull(meta.getSource());
        assertEquals("max", meta.getRecordedBy().getUsername());
        assertEquals("Max Mustermann", meta.getRecordedBy().getDisplayName());
    }

    @Test
    void diveraDraftFallsBackToReleasedByWhenOnlySystemNameStored() {
        User releaser = new User();
        releaser.setUsername("anna");
        releaser.setDisplayName("Anna Admin");

        IncidentReport report = new IncidentReport();
        report.setDiveraAlarmId(42L);
        report.setCreatedByName("DIVERA");
        report.setStatus(IncidentReportStatus.FREIGEGEBEN);
        report.setReleasedByUser(releaser);

        BerichteReportMetaSupport.MetaView meta = BerichteReportMetaSupport.forIncident(report);

        assertEquals("DIVERA", meta.getSource());
        assertEquals("anna", meta.getRecordedBy().getUsername());
        assertFalse(meta.showReleasedBySeparately());
    }
}
