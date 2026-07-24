package de.feuerwehr.manager.leitstellen;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Beim Löschen von Depeche.pdf / Abschlussbericht.pdf auch den Import-Eintrag entfernen,
 * damit ein erneuter Abruf die Datei wieder zuordnen kann.
 */
@Component
@RequiredArgsConstructor
public class LeitstellenAttachmentCleanup {

    private final LeitstellenMailImportRepository importRepository;

    @Transactional
    public void onAttachmentDeleted(long unitId, long reportId, String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        for (LeitstellenMailKind kind : LeitstellenMailKind.values()) {
            if (kind.storedFilename().equalsIgnoreCase(normalized)) {
                importRepository
                        .findByIncidentReportIdAndKind(reportId, kind)
                        .ifPresent(importRepository::delete);
                return;
            }
        }
    }
}
