package de.feuerwehr.manager.leitstellen;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hält Leitstellen-Import-Einträge konsistent, wenn Depeche_/Abschlussbericht_-PDFs
 * gelöscht oder umbenannt werden.
 */
@Component
@RequiredArgsConstructor
public class LeitstellenAttachmentCleanup {

    private final LeitstellenMailImportRepository importRepository;

    @Transactional
    public void onAttachmentDeleted(long unitId, long reportId, String filename) {
        LeitstellenMailKind.fromFilename(filename).ifPresent(kind -> importRepository
                .findByIncidentReportIdAndKind(reportId, kind)
                .ifPresent(importRepository::delete));
    }

    @Transactional
    public void onAttachmentRenamed(long unitId, long reportId, String oldFilename, String newFilename) {
        Optional<LeitstellenMailKind> oldKind = LeitstellenMailKind.fromFilename(oldFilename);
        Optional<LeitstellenMailKind> newKind = LeitstellenMailKind.fromFilename(newFilename);

        if (oldKind.isEmpty() && newKind.isEmpty()) {
            return;
        }

        if (oldKind.isPresent() && newKind.isPresent() && oldKind.get() == newKind.get()) {
            importRepository.findByIncidentReportIdAndKind(reportId, oldKind.get()).ifPresent(row -> {
                row.setStoredFilename(newFilename);
                importRepository.save(row);
            });
            return;
        }

        if (oldKind.isPresent() && newKind.isPresent()) {
            Optional<LeitstellenMailImport> existingNew =
                    importRepository.findByIncidentReportIdAndKind(reportId, newKind.get());
            if (existingNew.isPresent()) {
                throw new IllegalArgumentException(
                        "Umbenennung nicht möglich: Ein "
                                + newKind.get().baseName()
                                + "-Anhang ist für diesen Einsatz bereits als Leitstellen-Import hinterlegt.");
            }
            importRepository.findByIncidentReportIdAndKind(reportId, oldKind.get()).ifPresent(row -> {
                row.setKind(newKind.get());
                row.setStoredFilename(newFilename);
                importRepository.save(row);
            });
            return;
        }

        if (oldKind.isPresent()) {
            importRepository
                    .findByIncidentReportIdAndKind(reportId, oldKind.get())
                    .ifPresent(importRepository::delete);
        }
    }
}
