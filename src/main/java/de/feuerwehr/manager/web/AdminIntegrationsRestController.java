package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.DiveraAlarmsResponse;
import de.feuerwehr.manager.divera.DiveraImportResult;
import de.feuerwehr.manager.divera.DiveraImportService;
import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.mail.SmtpMailService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.print.CupsPrintService;
import de.feuerwehr.manager.print.UnitPrintSettingsService;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.unit.UnitSmtpAccount;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.web.dto.ActionResultDto;
import de.feuerwehr.manager.web.dto.CupsPrintersDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rest")
@RequiredArgsConstructor
public class AdminIntegrationsRestController {

    private final UnitService unitService;
    private final UnitAdminService unitAdminService;
    private final GlobalSettingsService globalSettingsService;
    private final SmtpMailService smtpMailService;
    private final DiveraService diveraService;
    private final DiveraImportService diveraImportService;
    private final UserRepository userRepository;
    private final UnitPrintSettingsService unitPrintSettingsService;

    @GetMapping("/unit/print/printers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
    @ResponseBody
    public CupsPrintersDto listCupsPrinters(@AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        try {
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            boolean available = unitPrintSettingsService.isCupsClientAvailable();
            var printers = unitPrintSettingsService.listCupsPrinters(unit);
            return CupsPrintersDto.success(available, printers);
        } catch (IllegalArgumentException e) {
            return CupsPrintersDto.failure(e.getMessage());
        }
    }

    @PostMapping("/unit/print/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
    @ResponseBody
    public ActionResultDto testPrint(@AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        try {
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            CupsPrintService.CupsPrintResult result = unitPrintSettingsService.sendTestPrint(unit);
            if (result.success()) {
                return ActionResultDto.success(result.message());
            }
            return ActionResultDto.failure(result.message());
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/unit/smtp/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
    @ResponseBody
    public ActionResultDto testUnitSmtp(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false) Long smtpAccountId) {
        try {
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            String to = resolveTestEmail(actor);
            UnitSmtpAccount unitSmtp = null;
            if (smtpAccountId != null && smtpAccountId > 0) {
                unitSmtp = unitAdminService.requireSmtpAccount(unit, smtpAccountId);
            } else {
                var accounts = unitAdminService.listSmtpAccounts(unit);
                if (accounts.size() == 1) {
                    unitSmtp = accounts.get(0);
                } else if (accounts.isEmpty()) {
                    unitSmtp = null;
                } else {
                    return ActionResultDto.failure(
                            "Bitte in der Tabelle beim gewünschten Konto auf „Test“ klicken.");
                }
            }
            Optional<String> err = smtpMailService.sendTestMail(unitSmtp, to);
            if (err.isPresent()) {
                return ActionResultDto.failure(err.get());
            }
            return ActionResultDto.success("Test-Mail wurde an " + to + " gesendet.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/global/smtp/test")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ActionResultDto testGlobalSmtp(@AuthenticationPrincipal AppUserDetails actor) {
        try {
            String to = resolveTestEmail(actor);
            Optional<String> err = smtpMailService.sendTestMail(globalSettingsService.get(), to);
            if (err.isPresent()) {
                return ActionResultDto.failure(err.get());
            }
            return ActionResultDto.success("Test-Mail wurde an " + to + " gesendet.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/unit/divera/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
    @ResponseBody
    public ActionResultDto testDivera(@AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        try {
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            DiveraAlarmsResponse response = diveraService.getAlarmsForUnit(unit);
            if (response.success()) {
                int n = response.alarms().size();
                String msg = n == 0
                        ? "Verbindung zu DIVERA erfolgreich (keine Alarme)."
                        : "Verbindung zu DIVERA erfolgreich (" + n + " Alarm(e)).";
                return ActionResultDto.success(msg);
            }
            return ActionResultDto.failure(response.message());
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/unit/divera/import")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
    @ResponseBody
    public ActionResultDto importDivera(@AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        try {
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            DiveraImportResult result = diveraImportService.importAlarmsForUnit(unit);
            if (!result.success()) {
                return ActionResultDto.failure(result.message());
            }
            return ActionResultDto.success(result.message(), result.imported(), result.skipped());
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    private String resolveTestEmail(AppUserDetails actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Nicht angemeldet.");
        }
        User user = userRepository
                .findById(actor.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        String to = user.getLoginEmail();
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException(
                    "Keine Login-E-Mail am Benutzer — bitte unter Benutzer → Bearbeiten hinterlegen.");
        }
        return to.trim();
    }
}
