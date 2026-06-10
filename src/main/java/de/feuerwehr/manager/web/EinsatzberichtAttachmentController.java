package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.EinsatzberichtAttachmentService;
import de.feuerwehr.manager.berichte.EinsatzberichtAttachmentService.DownloadFile;
import de.feuerwehr.manager.berichte.IncidentAttachmentDto;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/berichte/einsatzberichte")
@RequiredArgsConstructor
public class EinsatzberichtAttachmentController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final EinsatzberichtAttachmentService attachmentService;

    @GetMapping("/{id}/anhaenge")
    public List<IncidentAttachmentDto> list(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id) {
        long unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit);
        requireBerichteRead(actor, unit);
        return attachmentService.list(unit, id);
    }

    @PostMapping(value = "/{id}/anhaenge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IncidentAttachmentDto upload(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            @RequestParam("file") MultipartFile file) {
        long unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit);
        requireBerichteWrite(actor, unit);
        return attachmentService.upload(unit, id, file, actor.getUserId());
    }

    @GetMapping("/{id}/anhaenge/{aid}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            @PathVariable long aid) {
        long unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit);
        requireBerichteRead(actor, unit);
        DownloadFile file = attachmentService.download(unit, id, aid);
        String safeName = file.filename().replace("\"", "'");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.fileSize())
                .body(file.resource());
    }

    @DeleteMapping("/{id}/anhaenge/{aid}")
    public void delete(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            @PathVariable long aid) {
        long unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit);
        requireBerichteWrite(actor, unit);
        attachmentService.delete(unit, id, aid);
    }

    private long resolveUnit(Long unitId, AppUserDetails actor) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, unit.getId());
        return unit.getId();
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            throw new IllegalArgumentException("Das Modul Berichte ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireBerichteRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "berichte.read");
    }

    private void requireBerichteWrite(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "berichte.write");
    }
}
