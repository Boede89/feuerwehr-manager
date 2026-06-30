package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.DiveraAlarmSampleService;
import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.divera.DiveraWebhookService;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookOutcome;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookStatus;
import de.feuerwehr.manager.divera.TestDiveraAlarmService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.web.dto.ActionResultDto;
import de.feuerwehr.manager.web.dto.DiveraAlarmSampleListItemDto;
import de.feuerwehr.manager.web.dto.DiveraAlarmSamplePayloadDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final TestDiveraAlarmService testDiveraAlarmService;
    private final DiveraAlarmSampleService diveraAlarmSampleService;
    private final DiveraService diveraService;

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
        model.addAttribute("openTestAlarms", testDiveraAlarmService.listOpenForUnit(unit.getId()));
        diveraService.syncAlarmSamplesForUnit(unit.getId());
        model.addAttribute("alarmSamples", diveraAlarmSampleService.listForUnit(unit.getId()));
        return "test-alarm";
    }

    @GetMapping("/samples")
    @ResponseBody
    public List<DiveraAlarmSampleListItemDto> samples(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "sync", defaultValue = "true") boolean sync) {
        requireTestMode();
        unitService
                .resolveActiveUnit(unit, actor)
                .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
        if (sync) {
            diveraService.syncAlarmSamplesForUnit(unit);
        }
        return diveraAlarmSampleService.listForUnit(unit);
    }

    @PostMapping("/samples/{id}/start")
    @ResponseBody
    public ActionResultDto startSample(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            @RequestParam(name = "sendPush", defaultValue = "false") boolean sendPush) {
        try {
            requireTestMode();
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
            WebhookOutcome outcome = diveraAlarmSampleService.startEinsatzFromSample(unit, id);
            if (outcome.status() == WebhookStatus.ACCEPTED) {
                if (sendPush) {
                    diveraAlarmSampleService
                            .payloadForUnit(unit, id)
                            .ifPresent(payload -> diveraWebhookService.tryDispatchEinsatzAppPush(unit, payload));
                }
                String msg = outcome.message();
                if (sendPush) {
                    msg += " — Push-Versuch protokolliert (Einsatz-App → Letzte Push-Versuche)";
                }
                return ActionResultDto.success(msg);
            }
            return ActionResultDto.failure(
                    outcome.message() != null ? outcome.message() : "Einsatz konnte nicht gestartet werden");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/samples/{id}/delete")
    @ResponseBody
    public ActionResultDto deleteSample(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id) {
        try {
            requireTestMode();
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
            diveraAlarmSampleService.deleteSample(unit, id);
            return ActionResultDto.success("Beispiel-Einsatz gelöscht.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @GetMapping("/samples/{id}/payload")
    @ResponseBody
    public DiveraAlarmSamplePayloadDto samplePayload(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id) {
        try {
            requireTestMode();
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
            return diveraAlarmSampleService
                    .payloadForUnit(unit, id)
                    .map(DiveraAlarmSamplePayloadDto::success)
                    .orElseGet(() -> DiveraAlarmSamplePayloadDto.failure("Beispiel nicht gefunden"));
        } catch (IllegalArgumentException e) {
            return DiveraAlarmSamplePayloadDto.failure(e.getMessage());
        }
    }

    @PostMapping("/close")
    @ResponseBody
    public ActionResultDto close(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long id) {
        try {
            requireTestMode();
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
            testDiveraAlarmService.closeAlarm(unit, id);
            return ActionResultDto.success("Einsatz beendet — nicht mehr auf der Startseite.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/send")
    @ResponseBody
    public ActionResultDto send(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String payload,
            @RequestParam(name = "sendPush", defaultValue = "false") boolean sendPush) {
        try {
            requireTestMode();
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Kein Zugriff auf diese Einheit."));
            WebhookOutcome outcome = diveraWebhookService.handleTestWebhook(unit, payload, sendPush);
            if (outcome.status() == WebhookStatus.ACCEPTED) {
                String msg = outcome.message();
                if (sendPush) {
                    msg += " — Push-Versuch protokolliert (Einsatz-App → Letzte Push-Versuche)";
                }
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
