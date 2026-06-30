package de.feuerwehr.manager.einsatzapp;

import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper.DiveraAlarmDetails;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EinsatzAppPushService {

    private static final long TEST_ALARM_ID = 999_001L;

    private final ModuleSettingsService moduleSettingsService;
    private final EinsatzAppSettingsService einsatzAppSettingsService;
    private final EinsatzappDeviceTokenRepository deviceTokenRepository;
    private final EinsatzappPushLogRepository pushLogRepository;
    private final FcmPushClient fcmPushClient;
    private final UserPermissionService userPermissionService;
    private final UnitRepository unitRepository;

    @Transactional
    public void tryDispatchFromWebhook(long unitId, DiveraAlarmDetails details) {
        if (details == null || details.alarmId() <= 0) {
            recordSkipped(unitId, null, null, "Übersprungen: Ungültige Alarm-ID");
            return;
        }
        if (details.closed()) {
            recordSkipped(unitId, details.alarmId(), details.title(), "Übersprungen: Einsatz geschlossen");
            return;
        }
        if (!moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId)) {
            recordSkipped(unitId, details.alarmId(), details.title(), "Übersprungen: Modul Einsatz-App nicht aktiv");
            return;
        }
        if (!einsatzAppSettingsService.isPushEnabled(unitId)) {
            recordSkipped(
                    unitId, details.alarmId(), details.title(), "Übersprungen: Push für diese Einheit deaktiviert");
            return;
        }
        if (!fcmPushClient.isAvailable()) {
            recordSkipped(
                    unitId,
                    details.alarmId(),
                    details.title(),
                    "Übersprungen: FCM nicht konfiguriert (Dienstkonto-JSON fehlt)");
            return;
        }
        List<String> tokens = resolveTargetTokens(unitId);
        if (tokens.isEmpty()) {
            logPush(unitId, details.alarmId(), details.title(), 0, 0, "Keine registrierten Geräte");
            return;
        }
        String title = details.title() != null && !details.title().isBlank() ? details.title().trim() : "Einsatz";
        String body = buildBody(details);
        FcmPushClient.FcmSendResult result = fcmPushClient.sendAlarmNotification(tokens, title, body, details.alarmId());
        if (!result.invalidTokens().isEmpty()) {
            deviceTokenRepository.deleteByFcmTokenIn(result.invalidTokens());
        }
        String error = result.successCount() == 0 && result.failureCount() > 0 ? "FCM-Versand fehlgeschlagen" : null;
        logPush(unitId, details.alarmId(), details.title(), tokens.size(), result.successCount(), error);
        log.info(
                "[Einsatz-App] Push unit={} alarm={} targets={} sent={} failed={}",
                unitId,
                details.alarmId(),
                tokens.size(),
                result.successCount(),
                result.failureCount());
    }

    @Transactional
    public void sendTestPush(long unitId) {
        tryDispatchFromWebhook(unitId, testPushDetails());
    }

    @Transactional
    public void recordSkipped(long unitId, String reason) {
        recordSkipped(unitId, null, null, reason);
    }

    @Transactional
    public void recordSkipped(long unitId, Long alarmId, String alarmTitle, String reason) {
        logPush(unitId, alarmId, alarmTitle, 0, 0, reason);
        log.info("[Einsatz-App] Push übersprungen unit={}: {}", unitId, reason);
    }

    private List<String> resolveTargetTokens(long unitId) {
        List<String> tokens = new ArrayList<>();
        for (EinsatzappDeviceToken row : deviceTokenRepository.findByUnitId(unitId)) {
            if (row.getUser() == null || row.getFcmToken() == null || row.getFcmToken().isBlank()) {
                continue;
            }
            AppUserDetails actor = AppUserDetails.from(row.getUser());
            if (!userPermissionService.hasPermission(actor, unitId, "einsatzapp.read")) {
                continue;
            }
            tokens.add(row.getFcmToken().trim());
        }
        return tokens;
    }

    private static String buildBody(DiveraAlarmDetails details) {
        if (details.address() != null && !details.address().isBlank()) {
            return details.address().trim();
        }
        if (details.text() != null && !details.text().isBlank()) {
            String text = details.text().trim();
            return text.length() > 180 ? text.substring(0, 177) + "…" : text;
        }
        return "Neuer DIVERA-Einsatz";
    }

    private void logPush(
            long unitId, Long alarmId, String alarmTitle, int targeted, int sent, String errorMessage) {
        EinsatzappPushLog logEntry = new EinsatzappPushLog();
        logEntry.setUnit(unitRepository.getReferenceById(unitId));
        logEntry.setDiveraAlarmId(alarmId);
        logEntry.setAlarmTitle(alarmTitle);
        logEntry.setTokensTargeted(targeted);
        logEntry.setTokensSent(sent);
        logEntry.setErrorMessage(errorMessage);
        pushLogRepository.save(logEntry);
    }

    private static DiveraAlarmDetails testPushDetails() {
        long now = System.currentTimeMillis() / 1000;
        return new DiveraAlarmDetails(
                TEST_ALARM_ID,
                "einsatzapp-test",
                "Test-Push Einsatz-App",
                "Manueller Test vom Feuerwehr-Manager",
                null,
                now,
                now,
                0L,
                0L,
                0L,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
