package de.feuerwehr.manager.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.auswertung.AuswertungBereich;
import de.feuerwehr.manager.auswertung.AuswertungFilter;
import de.feuerwehr.manager.auswertung.AuswertungService;
import de.feuerwehr.manager.auswertung.AuswertungTypFilter;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
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
    private final AuswertungService auswertungService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "bereich", required = false) String bereichKey,
            @RequestParam(name = "jahr", required = false) Integer jahr,
            @RequestParam(name = "von", required = false) String von,
            @RequestParam(name = "bis", required = false) String bis,
            @RequestParam(name = "typ", required = false) String typ,
            @RequestParam(name = "thema", required = false) String thema,
            @RequestParam(name = "stichwort", required = false) String stichwort,
            @RequestParam(name = "personId", required = false) Long personId,
            @RequestParam(name = "vehicleId", required = false) Long vehicleId,
            @RequestParam(name = "zeitVon", required = false) String zeitVon,
            @RequestParam(name = "zeitBis", required = false) String zeitBis,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireRead(actor, unit.getId());

            AuswertungBereich bereich = AuswertungBereich.fromKey(bereichKey);
            int year = jahr != null ? jahr : LocalDate.now().getYear();
            LocalDate from = parseDate(von, LocalDate.of(year, 1, 1));
            LocalDate to = parseDate(bis, LocalDate.now());
            if (to.isBefore(from)) {
                to = from;
            }
            AuswertungFilter filter = new AuswertungFilter(
                    year,
                    from,
                    to,
                    AuswertungTypFilter.fromKey(typ),
                    blankToNull(thema),
                    blankToNull(stichwort),
                    personId != null && personId > 0 ? personId : null,
                    vehicleId != null && vehicleId > 0 ? vehicleId : null,
                    parseTime(zeitVon),
                    parseTime(zeitBis));

            model.addAttribute("bereich", bereich);
            model.addAttribute("bereiche", List.of(
                    AuswertungBereich.PERSONEN,
                    AuswertungBereich.EINSAETZE,
                    AuswertungBereich.FAHRZEUGE,
                    AuswertungBereich.GERAETE));
            model.addAttribute("filter", filter);
            model.addAttribute("typFilters", AuswertungTypFilter.values());
            model.addAttribute("personOptions", auswertungService.personOptions(unit.getId()));
            model.addAttribute("vehicleOptions", auswertungService.vehicleOptions(unit.getId()));
            model.addAttribute("themaOptions", auswertungService.themaOptions(unit.getId(), filter));
            model.addAttribute("stichwortOptions", auswertungService.stichwortOptions(unit.getId(), filter));
            model.addAttribute("currentYear", LocalDate.now().getYear());

            java.util.Map<String, Object> chartData = new java.util.LinkedHashMap<>();
            chartData.put("topPersonen", List.of());
            chartData.put("topStichworte", List.of());
            chartData.put("stichworte", List.of());
            chartData.put("themen", List.of());
            chartData.put("typ", List.of());
            chartData.put("fahrzeuge", List.of());
            chartData.put("geraete", List.of());

            switch (bereich) {
                case PERSONEN -> {
                    var rows = auswertungService.personStats(unit.getId(), filter);
                    model.addAttribute("personRows", rows);
                    chartData.put(
                            "topPersonen",
                            rows.stream()
                                    .limit(12)
                                    .map(r -> java.util.Map.of("label", r.displayName(), "value", r.teilnahmen()))
                                    .toList());
                    model.addAttribute(
                            "gesamtStundenPersonen",
                            rows.stream().mapToDouble(r -> r.stunden()).sum());
                }
                case EINSAETZE -> {
                    var summary = auswertungService.eventStats(unit.getId(), filter);
                    model.addAttribute("eventSummary", summary);
                    chartData.put("stichworte", summary.stichworte());
                    chartData.put("themen", summary.themen());
                    chartData.put(
                            "typ",
                            List.of(
                                    java.util.Map.of("label", "Einsätze", "value", summary.anzahlEinsaetze()),
                                    java.util.Map.of("label", "Übungen", "value", summary.anzahlUebungen()),
                                    java.util.Map.of("label", "Sonstiges", "value", summary.anzahlSonstiges())));
                }
                case FAHRZEUGE -> {
                    var rows = auswertungService.vehicleStats(unit.getId(), filter);
                    model.addAttribute("vehicleRows", rows);
                    chartData.put(
                            "fahrzeuge",
                            rows.stream()
                                    .limit(12)
                                    .map(r -> java.util.Map.of("label", r.vehicleName(), "value", r.gesamt()))
                                    .toList());
                }
                case GERAETE -> {
                    var rows = auswertungService.equipmentStats(unit.getId(), filter);
                    model.addAttribute("equipmentRows", rows);
                    chartData.put(
                            "geraete",
                            rows.stream()
                                    .limit(12)
                                    .map(r -> java.util.Map.of("label", r.equipmentName(), "value", r.anzahl()))
                                    .toList());
                }
                case UEBERSICHT -> {
                    var overview = auswertungService.overview(unit.getId(), filter);
                    model.addAttribute("overview", overview);
                    chartData.put("topPersonen", overview.topPersonen());
                    chartData.put("topStichworte", overview.topStichworte());
                    chartData.put(
                            "typ",
                            List.of(
                                    java.util.Map.of("label", "Einsätze", "value", overview.anzahlEinsaetze()),
                                    java.util.Map.of("label", "Übungen", "value", overview.anzahlUebungen()),
                                    java.util.Map.of("label", "Sonstiges", "value", overview.anzahlSonstiges())));
                }
            }
            model.addAttribute("chartDataJson", toJson(chartData));

            return "auswertung/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/?unit=" + (unitId != null ? unitId : "");
        }
    }

    private Unit resolveUnit(Long unitId, AppUserDetails actor, Model model) {
        Unit unit = unitService.resolveActiveUnit(unitId, actor);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
