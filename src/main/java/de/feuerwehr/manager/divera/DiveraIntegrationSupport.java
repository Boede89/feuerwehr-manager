package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.unit.UnitDiveraSettings;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class DiveraIntegrationSupport {

    private DiveraIntegrationSupport() {}

    public static final String DEFAULT_API_BASE = "https://app.divera247.com";

    public static String buildWebhookUrl(String appBaseUrl, long unitId, String webhookSecret) {
        String base = normalizeBase(appBaseUrl);
        String path = base + "/api/webhook/divera?unit=" + unitId;
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return path + "&secret=<DEIN_SECRET>";
        }
        return path + "&secret=" + URLEncoder.encode(webhookSecret.trim(), StandardCharsets.UTF_8);
    }

    public static String webhookUrlForSettings(String appBaseUrl, UnitDiveraSettings settings) {
        if (settings == null) {
            return buildWebhookUrl(appBaseUrl, 0, null);
        }
        return buildWebhookUrl(appBaseUrl, settings.getUnitId(), settings.getWebhookSecret());
    }

    private static String normalizeBase(String appBaseUrl) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return "";
        }
        String t = appBaseUrl.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
