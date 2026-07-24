package de.feuerwehr.manager.leitstellen;

import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hält Leitstellen-Import-Einträge konsistent, wenn Depeche.pdf / Abschlussbericht.pdf
 * gelöscht oder umbenannt werden.
 */
@Component
@RequiredArgsConstructor
public class LeitstellenAttachmentCleanup {

    private final LeitstellenMailImportRepository importRepository;

    @Transactional
    public void onAttachmentDeleted(long unitId, long reportId, String filename) {
        kindForFilename(filename).ifPresent(kind -> importRepository
                .findByIncidentReportIdAndKind(reportId, kind)
                .ifPresent(importRepository::delete));
    }

    @Transactional
    public void onAttachmentRenamed(long unitId, long reportId, String oldFilename, String newFilename) {
        Optional<LeitstellenMailKind> oldKind = kindForFilename(oldFilename);
        Optional<LeitstellenMailKind> newKind = kindForFilename(newFilename);

        if (oldKind.isEmpty() && newKind.isEmpty()) {
            return;
        }

        if (oldKind.isPresent() && newKind.isPresent() && oldKind.get() == newKind.get()) {
            importRepository.findByIncidentReportIdAndKind(reportId, oldKind.get()).ifPresent(row -> {
                row.setStoredFilename(newKind.get().storedFilename());
                importRepository.save(row);
            });
            return;
        }

        if (oldKind.isPresent() && newKind.isPresent()) {
            // z. B. Abschlussbericht.pdf → Depeche.pdf
            Optional<LeitstellenMailImport> existingNew =
                    importRepository.findByIncidentReportIdAndKind(reportId, newKind.get());
            if (existingNew.isPresent()) {
                throw new IllegalArgumentException(
                        "Umbenennung nicht möglich: \"" + newKind.get().storedFilename()
                                + "\" ist für diesen Einsatz bereits als Leitstellen-Import hinterlegt.");
            }
            importRepository.findByIncidentReportIdAndKind(reportId, oldKind.get()).ifPresent(row -> {
                row.setKind(newKind.get());
                row.setStoredFilename(newKind.get().storedFilename());
                importRepository.save(row);
            });
            return;
        }

        if (oldKind.isPresent()) {
            // Leitstellen-Name → anderer Name: Import-Eintrag entfernen
            importRepository
                    .findByIncidentReportIdAndKind(reportId, oldKind.get())
                    .ifPresent(importRepository::delete);
        }
        // anderer Name → Leitstellen-Name: kein Import-Eintrag anlegen (kein Mail-SHA bekannt)
    }

    private static Optional<LeitstellenMailKind> kindForFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        for (LeitstellenMailKind kind : LeitstellenMailKind.values()) {
            if (kind.storedFilename().equalsIgnoreCase(normalized)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
