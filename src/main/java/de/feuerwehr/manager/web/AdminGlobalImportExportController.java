package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.transfer.DatabaseBackupService;
import de.feuerwehr.manager.transfer.DatabaseBackupService.DatabaseImportSummary;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/global/import-export")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminGlobalImportExportController {

    private final AccessControlService accessControlService;
    private final DatabaseBackupService databaseBackupService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserDetails actor) {
        accessControlService.requireSuperAdmin(actor);

        byte[] sql = databaseBackupService.exportSql();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "feuerwehr-manager-backup-" + stamp + ".sql";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("application", "sql"))
                .body(sql);
    }

    @PostMapping("/import")
    public String importData(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam("importFile") MultipartFile importFile,
            @RequestParam(name = "confirmRestore", defaultValue = "false") boolean confirmRestore,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireSuperAdmin(actor);

            if (importFile == null || importFile.isEmpty()) {
                throw new IllegalArgumentException("Bitte eine Datei auswählen.");
            }
            if (!confirmRestore) {
                throw new IllegalArgumentException(
                        "Zum Wiederherstellen der Datenbank bitte die Bestätigung aktivieren.");
            }

            DatabaseImportSummary summary = databaseBackupService.importSql(importFile.getBytes());

            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", summary.message());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin?scope=global&tab=import-export";
    }
}
