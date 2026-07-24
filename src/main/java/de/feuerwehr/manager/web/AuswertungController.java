package de.feuerwehr.manager.web;

import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.auswertung.AuswertungBereich;
import de.feuerwehr.manager.auswertung.AuswertungOverviewStats;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auswertung")
@RequiredArgsConstructor
public class AuswertungController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final PersonalService personalService;
    private final AtemschutzService atemschutzService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "bereich", required = false) String bereichKey,
            @RequestParam(name = "jahr", required = false) Integer jahr,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireRead(actor, unit.getId());

            AuswertungBereich bereich = AuswertungBereich.fromKey(bereichKey);
            int filterYear = jahr != null ? jahr : LocalDate.now().getYear();
            int currentYear = LocalDate.now().getYear();
            List<Integer> yearOptions = new ArrayList<>();
            for (int y = currentYear; y >= currentYear - 10; y--) {
                yearOptions.add(y);
            }

            model.addAttribute("bereich", bereich);
            model.addAttribute(
                    "bereiche",
                    List.of(
                            AuswertungBereich.UEBERSICHT,
                            AuswertungBereich.PERSONEN,
                            AuswertungBereich.EINSAETZE,
                            AuswertungBereich.FAHRZEUGE,
                            AuswertungBereich.GERAETE));
            model.addAttribute("filterYear", filterYear);
            model.addAttribute("yearOptions", yearOptions);

            if (bereich == AuswertungBereich.UEBERSICHT) {
                model.addAttribute("overviewStats", buildOverviewStats(unit.getId()));
            }

            return "auswertung/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/?unit=" + (unitId != null ? unitId : "");
        }
    }

    private AuswertungOverviewStats buildOverviewStats(long unitId) {
        int mitglieder = personalService.listPersons(unitId).size();
        int tauglichePa = 0;
        try {
            if (moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)) {
                tauglichePa = atemschutzService.listCarrierOverviews(unitId, "all").stats().tauglich();
            }
        } catch (RuntimeException ignored) {
            // z. B. fehlende Rechte/Konfiguration — Karte bleibt bei 0
            tauglichePa = 0;
        }
        // Einsatz-/Übungs-Zähllogik folgt später.
        return new AuswertungOverviewStats(0, 0, 0, 0, 0, mitglieder, tauglichePa);
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

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.AUSWERTUNG, unitId)) {
            throw new IllegalArgumentException("Das Modul Auswertung ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "auswertung.read");
    }
}
