package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.DiveraWebhookService;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookOutcome;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookStatus;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.web.dto.ActionResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/test-alarm")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
public class TestAlarmController {

    static final String SAMPLE_JSON =
            """
            {
              "data": {
                "id": 999001,
                "title": "B3 Sturm – Testalarm",
                "text": "Simulierter Einsatz aus dem Testmodus (kein DIVERA-Aufruf).",
                "address": "Musterstraße 1, 12345 Musterstadt",
                "foreign_id": "testalarm-local-001",
                "ts_create": 1717234567,
                "closed": false
              }
            }
            """;

    private final TestModeService testModeService;
    private final UnitService unitService;
    private final DiveraWebhookService diveraWebhookService;

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
        model.addAttribute("testAlarmUnitId", unit.getId());
        model.addAttribute("sampleJson", SAMPLE_JSON.trim());
        return "test-alarm";
    }

    @PostMapping("/send")
    @ResponseBody
    public ActionResultDto send(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String payload) {
        try {
            requireTestMode();
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
            WebhookOutcome outcome = diveraWebhookService.handleTestWebhook(unit, payload);
            if (outcome.status() == WebhookStatus.ACCEPTED) {
                String msg = outcome.message();
                if (outcome.externalId() != null && !outcome.externalId().isBlank()) {
                    msg += " (ID: " + outcome.externalId() + ")";
                }
                return ActionResultDto.success(msg);
            }
            return ActionResultDto.failure(
                    outcome.message() != null ? outcome.message() : "Verarbeitung fehlgeschlagen");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    private void requireTestMode() {
        if (!testModeService.isEnabled()) {
            throw new IllegalArgumentException("Testalarm ist nur bei aktivem Testmodus verfügbar.");
        }
    }
}
