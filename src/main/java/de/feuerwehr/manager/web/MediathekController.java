package de.feuerwehr.manager.web;

import de.feuerwehr.manager.mediathek.MediathekAccessLevel;
import de.feuerwehr.manager.mediathek.MediathekFile;
import de.feuerwehr.manager.mediathek.MediathekFolder;
import de.feuerwehr.manager.mediathek.MediathekService;
import de.feuerwehr.manager.mediathek.MediathekService.AclFormData;
import de.feuerwehr.manager.mediathek.MediathekService.AclInput;
import de.feuerwehr.manager.mediathek.MediathekService.FileDownload;
import de.feuerwehr.manager.mediathek.MediathekService.FolderView;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mediathek")
@RequiredArgsConstructor
public class MediathekController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final MediathekService mediathekService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "folder", required = false) Long folderId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireRead(actor, unit.getId());
            FolderView view = mediathekService.openFolder(actor, unit.getId(), folderId);
            model.addAttribute("pageTitle", "Mediathek");
            model.addAttribute("folderView", view);
            model.addAttribute("currentFolder", view.folder());
            model.addAttribute("breadcrumb", view.breadcrumb());
            model.addAttribute("children", view.children());
            model.addAttribute("files", view.files());
            model.addAttribute("canWrite", view.canWrite() && canWrite(actor, unit.getId()));
            model.addAttribute("canManageAcl", view.canManageAcl() && canWrite(actor, unit.getId()));
            model.addAttribute("isAdminLevel", actor.getRole().isAdminLevel());
            model.addAttribute("assignableUnits", mediathekService.listAssignableUnits(actor));
            model.addAttribute("accessLevels", MediathekAccessLevel.values());
            return "mediathek/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @GetMapping("/folders/{folderId}/rechte")
    public String aclForm(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long folderId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireWrite(actor, unit.getId());
            AclFormData form = mediathekService.loadAclForm(actor, unit.getId(), folderId);
            model.addAttribute("pageTitle", "Mediathek – Rechte");
            model.addAttribute("aclFolder", form.folder());
            model.addAttribute("aclEntries", form.entries());
            model.addAttribute("aclPersons", form.persons());
            model.addAttribute("aclGroups", form.groups());
            model.addAttribute("aclQualifications", form.qualifications());
            model.addAttribute("aclUnits", form.units());
            model.addAttribute("accessLevels", MediathekAccessLevel.values());
            return "mediathek/rechte";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectFolder(unitId, folderId);
        }
    }

    @PostMapping("/folders")
    public String createFolder(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "parentId", required = false) Long parentId,
            @RequestParam(name = "name") String name,
            @RequestParam(name = "sharedUnitIds", required = false) List<Long> sharedUnitIds,
            @RequestParam(name = "inheritAcl", defaultValue = "false") boolean inheritAcl,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            MediathekFolder created =
                    mediathekService.createFolder(actor, unitId, parentId, name, sharedUnitIds, inheritAcl);
            redirectAttributes.addFlashAttribute("success", "Ordner angelegt.");
            return redirectFolder(unitId, created.getId());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectFolder(unitId, parentId);
        }
    }

    @PostMapping("/folders/{folderId}/rename")
    public String renameFolder(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long folderId,
            @RequestParam(name = "name") String name,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            mediathekService.renameFolder(actor, unitId, folderId, name);
            redirectAttributes.addFlashAttribute("success", "Ordner umbenannt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectFolder(unitId, folderId);
    }

    @PostMapping("/folders/{folderId}/delete")
    public String deleteFolder(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long folderId,
            RedirectAttributes redirectAttributes) {
        Long parentId = null;
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            FolderView before = mediathekService.openFolder(actor, unitId, folderId);
            if (before.folder() != null && before.folder().getParent() != null) {
                parentId = before.folder().getParent().getId();
            }
            mediathekService.deleteFolder(actor, unitId, folderId);
            redirectAttributes.addFlashAttribute("success", "Ordner gelöscht.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectFolder(unitId, folderId);
        }
        return redirectFolder(unitId, parentId);
    }

    @PostMapping("/folders/{folderId}/units")
    public String updateUnits(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long folderId,
            @RequestParam(name = "sharedUnitIds", required = false) List<Long> sharedUnitIds,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            mediathekService.updateFolderUnits(actor, unitId, folderId, sharedUnitIds);
            redirectAttributes.addFlashAttribute("success", "Einheiten aktualisiert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectFolder(unitId, folderId);
    }

    @PostMapping("/folders/{folderId}/acl")
    public String saveAcl(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long folderId,
            @RequestParam(name = "inheritAcl", defaultValue = "false") boolean inheritAcl,
            @RequestParam(name = "aclPersonId", required = false) List<Long> personIds,
            @RequestParam(name = "aclGroupId", required = false) List<Long> groupIds,
            @RequestParam(name = "aclQualificationTypeId", required = false) List<Long> qualificationTypeIds,
            @RequestParam(name = "aclLevel", required = false) List<String> levels,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            mediathekService.replaceAcl(
                    actor,
                    unitId,
                    folderId,
                    inheritAcl,
                    parseAclInputs(personIds, groupIds, qualificationTypeIds, levels));
            redirectAttributes.addFlashAttribute("success", "Rechte gespeichert.");
            return redirectFolder(unitId, folderId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/mediathek/folders/" + folderId + "/rechte?unit=" + unitId;
        }
    }

    @PostMapping("/folders/{folderId}/upload")
    public String upload(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long folderId,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            MediathekFile uploaded = mediathekService.upload(actor, unitId, folderId, file);
            redirectAttributes.addFlashAttribute("success", "Datei hochgeladen: " + uploaded.getOriginalName());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectFolder(unitId, folderId);
    }

    @PostMapping("/files/{fileId}/delete")
    public String deleteFile(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "folder") long folderId,
            @PathVariable long fileId,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            mediathekService.deleteFile(actor, unitId, fileId);
            redirectAttributes.addFlashAttribute("success", "Datei gelöscht.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectFolder(unitId, folderId);
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long fileId) {
        Unit unit = resolveUnitOrThrow(unitId, actor);
        requireModuleEnabled(unit.getId());
        requireRead(actor, unit.getId());
        FileDownload file = mediathekService.download(actor, unit.getId(), fileId);
        return resourceResponse(file, false);
    }

    @GetMapping("/files/{fileId}/view")
    public ResponseEntity<org.springframework.core.io.Resource> view(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long fileId) {
        Unit unit = resolveUnitOrThrow(unitId, actor);
        requireModuleEnabled(unit.getId());
        requireRead(actor, unit.getId());
        FileDownload file = mediathekService.download(actor, unit.getId(), fileId);
        return resourceResponse(file, true);
    }

    private static ResponseEntity<org.springframework.core.io.Resource> resourceResponse(
            FileDownload file, boolean inline) {
        String safeName = file.filename().replace("\"", "'");
        String disposition = (inline ? "inline" : "attachment") + "; filename=\"" + safeName + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.fileSize())
                .body(file.resource());
    }

    private static List<AclInput> parseAclInputs(
            List<Long> personIds,
            List<Long> groupIds,
            List<Long> qualificationTypeIds,
            List<String> levels) {
        List<AclInput> result = new ArrayList<>();
        int n = levels == null ? 0 : levels.size();
        for (int i = 0; i < n; i++) {
            String rawLevel = levels.get(i);
            if (rawLevel == null || rawLevel.isBlank()) {
                continue;
            }
            MediathekAccessLevel level;
            try {
                level = MediathekAccessLevel.valueOf(rawLevel.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Long personId = personIds != null && i < personIds.size() ? personIds.get(i) : null;
            Long groupId = groupIds != null && i < groupIds.size() ? groupIds.get(i) : null;
            Long qualificationTypeId =
                    qualificationTypeIds != null && i < qualificationTypeIds.size()
                            ? qualificationTypeIds.get(i)
                            : null;
            boolean hasPerson = personId != null && personId > 0;
            boolean hasGroup = groupId != null && groupId > 0;
            boolean hasQualification = qualificationTypeId != null && qualificationTypeId > 0;
            int targets = (hasPerson ? 1 : 0) + (hasGroup ? 1 : 0) + (hasQualification ? 1 : 0);
            if (targets != 1) {
                continue;
            }
            result.add(new AclInput(
                    hasPerson ? personId : null,
                    hasGroup ? groupId : null,
                    hasQualification ? qualificationTypeId : null,
                    level));
        }
        return result;
    }

    private Unit resolveUnit(Long unitId, AppUserDetails actor, Model model) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, unit.getId());
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("currentUnitName", unit.getName());
        return unit;
    }

    private Unit resolveUnitOrThrow(Long unitId, AppUserDetails actor) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, unit.getId());
        return unit;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.MEDIATHEK, unitId)) {
            throw new IllegalArgumentException("Das Modul Mediathek ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireRead(AppUserDetails actor, long unitId) {
        if (actor != null && actor.getRole().isAdminLevel()) {
            return;
        }
        userPermissionService.requirePermission(actor, unitId, "mediathek.read");
    }

    private void requireWrite(AppUserDetails actor, long unitId) {
        if (actor != null && actor.getRole().isAdminLevel()) {
            return;
        }
        userPermissionService.requirePermission(actor, unitId, "mediathek.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        if (actor != null && actor.getRole().isAdminLevel()) {
            return true;
        }
        return userPermissionService.hasPermission(actor, unitId, "mediathek.write");
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }

    private static String redirectFolder(long unitId, Long folderId) {
        if (folderId == null || folderId <= 0) {
            return "redirect:/mediathek?unit=" + unitId;
        }
        return "redirect:/mediathek?unit=" + unitId + "&folder=" + folderId;
    }
}
