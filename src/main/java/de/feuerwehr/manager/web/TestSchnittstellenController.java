package de.feuerwehr.manager.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.divera.DiveraApiClient;
import de.feuerwehr.manager.divera.DiveraApiClient.RawApiResponse;
import de.feuerwehr.manager.divera.DiveraIntegrationSupport;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.web.dto.DiveraRawApiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/test-schnittstellen")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
public class TestSchnittstellenController {

    private final TestModeService testModeService;
    private final UnitService unitService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final DiveraApiClient diveraApiClient;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String page(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitParam,
            Model model) {
        if (!testModeService.isEnabled()) {
            return "redirect:/";
        }
        Unit unit = unitService
                .resolveActiveUnit(unitParam, actor)
                .orElseThrow(() -> new IllegalArgumentException("Bitte zuerst eine Einheit auswählen."));
        DiveraRawApiDto snapshot = loadSnapshot(unit.getId());
        model.addAttribute("schnittstellenUnitId", unit.getId());
        model.addAttribute("snapshot", snapshot);
        model.addAttribute("alarmsJson", prettyOrEmpty(snapshot.alarmsJson()));
        model.addAttribute("usersJson", prettyOrEmpty(snapshot.usersJson()));
        return "test-schnittstellen";
    }

    @GetMapping("/api/raw")
    @ResponseBody
    public DiveraRawApiDto rawApi(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        requireTestMode();
        unitService
                .resolveActiveUnit(unit, actor)
                .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
        return loadSnapshot(unit);
    }

    private DiveraRawApiDto loadSnapshot(long unitId) {
        UnitDiveraSettings cfg = diveraSettingsRepository.findByUnitId(unitId).orElse(null);
        if (cfg == null || cfg.getAccessKey() == null || cfg.getAccessKey().isBlank()) {
            return DiveraRawApiDto.failure("Keine DIVERA-Einstellungen oder Access Key für diese Einheit.");
        }
        String apiBase = cfg.getApiBaseUrl() != null && !cfg.getApiBaseUrl().isBlank()
                ? cfg.getApiBaseUrl().trim()
                : DiveraIntegrationSupport.DEFAULT_API_BASE;
        RawApiResponse alarms = diveraApiClient.fetchRawAlarms(apiBase, cfg.getAccessKey());
        RawApiResponse users = diveraApiClient.fetchRawUsers(apiBase, cfg.getAccessKey());
        boolean ok = alarms.success() || users.success();
        String message = ok ? "DIVERA-API abgerufen" : firstNonBlank(alarms.message(), users.message(), "Abruf fehlgeschlagen");
        return new DiveraRawApiDto(
                ok,
                message,
                apiBase,
                alarms.endpoint(),
                users.endpoint(),
                prettyOrEmpty(alarms.body()),
                prettyOrEmpty(users.body()),
                alarms.httpStatus() > 0 ? alarms.httpStatus() : null,
                users.httpStatus() > 0 ? users.httpStatus() : null);
    }

    private String prettyOrEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void requireTestMode() {
        if (!testModeService.isEnabled()) {
            throw new IllegalArgumentException("Schnittstellen-Debug ist nur bei aktivem Testmodus verfügbar.");
        }
    }
}
