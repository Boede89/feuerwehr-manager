package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.transfer.UnitPersonalAtemschutzTransferService;
import de.feuerwehr.manager.transfer.UnitPersonalAtemschutzTransferService.ImportSummary;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.nio.charset.StandardCharsets;
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
@RequestMapping("/admin/unit/import-export")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
@RequiredArgsConstructor
public class AdminUnitImportExportController {

    private final UnitService unitService;
    private final AccessControlService accessControlService;
    private final UnitPersonalAtemschutzTransferService transferService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        accessControlService.requireAdminLevel(actor);
        Unit resolved = unitService
                .resolveActiveUnit(unit, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, resolved.getId());

        byte[] json = transferService.exportJson(resolved.getId());
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "personal-atemschutz-export-unit" + resolved.getId() + "-" + stamp + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PostMapping("/import")
    public String importData(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "importFormat", defaultValue = "legacy-sql") String importFormat,
            @RequestParam("importFile") MultipartFile importFile,
            @RequestParam(name = "sourceEinheitId", required = false) Integer sourceEinheitId,
            @RequestParam(name = "replaceExisting", defaultValue = "false") boolean replaceExisting,
            @RequestParam(name = "confirmReplace", defaultValue = "false") boolean confirmReplace,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit resolved = unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, resolved.getId());

            if (importFile == null || importFile.isEmpty()) {
                throw new IllegalArgumentException("Bitte eine Datei auswählen.");
            }
            if (replaceExisting && !confirmReplace) {
                throw new IllegalArgumentException(
                        "Zum Ersetzen vorhandener Daten bitte die Bestätigung aktivieren.");
            }

            ImportSummary summary;
            if ("json".equalsIgnoreCase(importFormat)) {
                summary = transferService.importJson(resolved.getId(), importFile.getBytes(), replaceExisting);
            } else if ("legacy-sql".equalsIgnoreCase(importFormat)) {
                String sql = new String(importFile.getBytes(), StandardCharsets.UTF_8);
                summary = transferService.importLegacySql(resolved.getId(), sql, sourceEinheitId, replaceExisting);
            } else {
                throw new IllegalArgumentException("Unbekanntes Import-Format.");
            }

            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Import abgeschlossen: " + summary.message());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin?scope=einheit&tab=import-export&unit=" + unit;
    }
}
