package de.feuerwehr.manager.web;

import de.feuerwehr.manager.atemschutz.AtemschutzEntryRequest;
import de.feuerwehr.manager.atemschutz.AtemschutzEntryRequestService;
import de.feuerwehr.manager.atemschutz.AtemschutzEntryRequestService.CarrierOption;
import de.feuerwehr.manager.atemschutz.AtemschutzEntryRequestType;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/atemschutz/antraege")
@RequiredArgsConstructor
public class AtemschutzEntryRequestController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final AtemschutzEntryRequestService entryRequestService;

    @GetMapping("/api/carriers")
    @ResponseBody
    public ResponseEntity<?> listCarriers(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId) {
        try {
            requireModuleAndAccess(actor, unitId);
            List<CarrierOption> carriers = entryRequestService.listActiveCarrierOptions(unitId);
            return ResponseEntity.ok(Map.of("carriers", carriers, "today", LocalDate.now().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/submit")
    @ResponseBody
    public ResponseEntity<?> submit(
            @AuthenticationPrincipal AppUserDetails actor, @RequestBody SubmitPayload payload) {
        try {
            if (payload == null || payload.unitId() == null) {
                throw new IllegalArgumentException("Einheit fehlt.");
            }
            requireModuleAndAccess(actor, payload.unitId());
            AtemschutzEntryRequestType type = AtemschutzEntryRequestType.fromRaw(payload.entryType());
            LocalDate eventDate =
                    payload.eventDate() != null ? LocalDate.parse(payload.eventDate()) : LocalDate.now();
            AtemschutzEntryRequest saved = entryRequestService.submit(
                    payload.unitId(), type, eventDate, payload.carrierIds(), actor.getUserId());
            return ResponseEntity.ok(Map.of(
                    "ok",
                    true,
                    "id",
                    saved.getId(),
                    "message",
                    "Antrag wurde übermittelt und zur Freigabe weitergeleitet."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Antrag konnte nicht gespeichert werden."));
        }
    }

    @GetMapping
    public String list(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireReviewer(actor, unit.getId());
            List<AtemschutzEntryRequest> pending = entryRequestService.listPending(unit.getId());
            model.addAttribute("pendingRequests", pending);
            model.addAttribute("entryTypes", AtemschutzEntryRequestType.values());
            return "atemschutz/antraege";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @GetMapping("/{id}")
    public String detail(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable("id") long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            AtemschutzEntryRequest request = entryRequestService.requireRequest(id);
            Unit unit = resolveUnit(
                    unitId != null ? unitId : request.getUnit().getId(), actor, model);
            requireModuleEnabled(unit.getId());
            requireReviewer(actor, unit.getId());
            if (!request.getUnit().getId().equals(unit.getId())) {
                throw new IllegalArgumentException("Antrag gehört nicht zu dieser Einheit.");
            }
            List<CarrierOption> allCarriers = entryRequestService.listActiveCarrierOptions(unit.getId());
            Set<Long> selectedIds = request.getCarriers().stream()
                    .map(c -> c.getId())
                    .collect(Collectors.toSet());
            model.addAttribute("request", request);
            model.addAttribute("entryTypes", AtemschutzEntryRequestType.values());
            model.addAttribute("allCarriers", allCarriers);
            model.addAttribute("selectedCarrierIds", selectedIds);
            model.addAttribute("isPending", request.getStatus().name().equals("PENDING"));
            return "atemschutz/antrag-detail";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null
                    ? "redirect:/atemschutz/antraege?unit=" + unitId
                    : "redirect:/atemschutz/antraege";
        }
    }

    @PostMapping("/{id}/approve")
    public String approve(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable("id") long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "entryType") String entryTypeRaw,
            @RequestParam(name = "eventDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(name = "carrierIds", required = false) Long[] carrierIds,
            RedirectAttributes redirectAttributes) {
        Long redirectUnit = unitId;
        try {
            AtemschutzEntryRequest existing = entryRequestService.requireRequest(id);
            redirectUnit = existing.getUnit().getId();
            requireModuleAndAccess(actor, redirectUnit);
            requireReviewer(actor, redirectUnit);
            List<Long> ids = carrierIds == null ? List.of() : Arrays.asList(carrierIds);
            entryRequestService.approve(
                    id,
                    AtemschutzEntryRequestType.fromRaw(entryTypeRaw),
                    eventDate,
                    ids,
                    actor.getUserId(),
                    null);
            redirectAttributes.addFlashAttribute(
                    "success", "Antrag genehmigt — Nachweise wurden eingetragen.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectUnit != null
                    ? "redirect:/atemschutz/antraege/" + id + "?unit=" + redirectUnit
                    : "redirect:/atemschutz/antraege/" + id;
        }
        return "redirect:/atemschutz/antraege?unit=" + redirectUnit;
    }

    @PostMapping("/{id}/reject")
    public String reject(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable("id") long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            RedirectAttributes redirectAttributes) {
        Long redirectUnit = unitId;
        try {
            AtemschutzEntryRequest existing = entryRequestService.requireRequest(id);
            redirectUnit = existing.getUnit().getId();
            requireModuleAndAccess(actor, redirectUnit);
            requireReviewer(actor, redirectUnit);
            entryRequestService.reject(id, actor.getUserId(), null);
            redirectAttributes.addFlashAttribute("success", "Antrag wurde abgelehnt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectUnit != null
                    ? "redirect:/atemschutz/antraege/" + id + "?unit=" + redirectUnit
                    : "redirect:/atemschutz/antraege/" + id;
        }
        return "redirect:/atemschutz/antraege?unit=" + redirectUnit;
    }

    @PostMapping("/{id}/save")
    public String saveDraft(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable("id") long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "entryType") String entryTypeRaw,
            @RequestParam(name = "eventDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(name = "carrierIds", required = false) Long[] carrierIds,
            RedirectAttributes redirectAttributes) {
        Long redirectUnit = unitId;
        try {
            AtemschutzEntryRequest existing = entryRequestService.requireRequest(id);
            redirectUnit = existing.getUnit().getId();
            requireModuleAndAccess(actor, redirectUnit);
            requireReviewer(actor, redirectUnit);
            List<Long> ids = carrierIds == null ? List.of() : Arrays.asList(carrierIds);
            entryRequestService.updatePending(
                    id, AtemschutzEntryRequestType.fromRaw(entryTypeRaw), eventDate, ids);
            redirectAttributes.addFlashAttribute("success", "Antrag wurde aktualisiert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectUnit != null
                ? "redirect:/atemschutz/antraege/" + id + "?unit=" + redirectUnit
                : "redirect:/atemschutz/antraege/" + id;
    }

    private void requireModuleAndAccess(AppUserDetails actor, long unitId) {
        accessControlService.requireUnitAccess(actor, unitId);
        requireModuleEnabled(unitId);
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)) {
            throw new IllegalArgumentException("Das Modul Atemschutz ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireReviewer(AppUserDetails actor, long unitId) {
        boolean write = userPermissionService.hasPermission(actor, unitId, "atemschutz.write");
        if (!entryRequestService.isReviewer(unitId, actor.getUserId(), write)) {
            throw new IllegalArgumentException("Keine Berechtigung zur Bearbeitung von Atemschutz-Anträgen.");
        }
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

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }

    public record SubmitPayload(Long unitId, String entryType, String eventDate, List<Long> carrierIds) {}
}
