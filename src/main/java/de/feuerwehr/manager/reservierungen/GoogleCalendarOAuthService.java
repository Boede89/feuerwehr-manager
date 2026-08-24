package de.feuerwehr.manager.reservierungen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.UnitCalendarAccount;
import de.feuerwehr.manager.unit.UnitCalendarAccountRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarOAuthService {

    static final String OAUTH_SCOPES =
            "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/userinfo.email";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    private final GlobalSettingsService globalSettingsService;
    private final UnitCalendarAccountRepository calendarAccountRepository;
    private final ObjectMapper objectMapper;

    public record OAuthTokenResult(String accessToken, String refreshToken, String userEmail) {}

    public boolean isOAuthClientConfigured() {
        ApplicationSettings settings = globalSettingsService.get();
        return settings.getGoogleOauthClientId() != null
                && !settings.getGoogleOauthClientId().isBlank()
                && settings.getGoogleOauthClientSecret() != null
                && !settings.getGoogleOauthClientSecret().isBlank();
    }

    public String redirectUri() {
        ApplicationSettings settings = globalSettingsService.get();
        String base = settings.getAppUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException(
                    "App-URL fehlt – bitte unter Admin → Global → Konfiguration eintragen (für Google OAuth).");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/admin/unit/calendar/oauth/callback";
    }

    public String buildAuthorizationUrl(String state) {
        if (!isOAuthClientConfigured()) {
            throw new IllegalArgumentException(
                    "Google OAuth ist nicht konfiguriert – Client-ID und Secret unter Admin → Global → Konfiguration eintragen.");
        }
        ApplicationSettings settings = globalSettingsService.get();
        return AUTH_URL
                + "?client_id="
                + encode(settings.getGoogleOauthClientId().trim())
                + "&redirect_uri="
                + encode(redirectUri())
                + "&response_type=code"
                + "&scope="
                + encode(OAUTH_SCOPES)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state="
                + encode(state);
    }

    @Transactional
    public OAuthTokenResult completeAuthorization(long unitId, long calendarAccountId, String code) {
        UnitCalendarAccount account = calendarAccountRepository
                .findByIdAndUnitId(calendarAccountId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Kalender nicht gefunden."));
        OAuthTokenResult tokens = exchangeAuthorizationCode(code);
        if (tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
            throw new IllegalArgumentException(
                    "Google hat keinen Refresh-Token geliefert – bitte Verbindung trennen und erneut verbinden.");
        }
        account.setGoogleOauthRefreshToken(tokens.refreshToken());
        account.setGoogleOauthUserEmail(tokens.userEmail());
        account.setUpdatedAt(java.time.Instant.now());
        calendarAccountRepository.save(account);
        return tokens;
    }

    @Transactional
    public void disconnect(long unitId, long calendarAccountId) {
        UnitCalendarAccount account = calendarAccountRepository
                .findByIdAndUnitId(calendarAccountId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Kalender nicht gefunden."));
        account.setGoogleOauthRefreshToken(null);
        account.setGoogleOauthUserEmail(null);
        account.setUpdatedAt(java.time.Instant.now());
        calendarAccountRepository.save(account);
    }

    public Optional<String> accessTokenFor(UnitCalendarAccount account) {
        if (account.getGoogleOauthRefreshToken() == null || account.getGoogleOauthRefreshToken().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(refreshAccessToken(account.getGoogleOauthRefreshToken()));
        } catch (Exception e) {
            log.warn("Google OAuth Refresh für Kalender {} fehlgeschlagen: {}", account.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    public OAuthTokenResult exchangeAuthorizationCode(String code) {
        JsonNode token = postTokenForm(Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri(),
                "client_id", clientId(),
                "client_secret", clientSecret()));
        String accessToken = token.path("access_token").asText(null);
        String refreshToken = token.path("refresh_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Google OAuth: Access Token fehlt in der Antwort.");
        }
        String email = fetchUserEmail(accessToken).orElse(null);
        return new OAuthTokenResult(accessToken, refreshToken, email);
    }

    public String refreshAccessToken(String refreshToken) {
        JsonNode token = postTokenForm(Map.of(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "client_id", clientId(),
                "client_secret", clientSecret()));
        String accessToken = token.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Google OAuth: Access Token konnte nicht erneuert werden.");
        }
        return accessToken;
    }

    private Optional<String> fetchUserEmail(String accessToken) {
        try {
            RestClient client = buildClient(accessToken);
            String raw = client.get().uri(USERINFO_URL).retrieve().body(String.class);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String email = objectMapper.readTree(raw).path("email").asText(null);
            return email != null && !email.isBlank() ? Optional.of(email.trim()) : Optional.empty();
        } catch (Exception e) {
            log.debug("Google userinfo nicht lesbar: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode postTokenForm(Map<String, String> fields) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        fields.forEach(form::add);
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory())
                    .build();
            String raw = client
                    .post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Google OAuth: leere Token-Antwort.");
            }
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("error")) {
                String msg = root.path("error_description").asText(root.path("error").asText("OAuth-Fehler"));
                throw new IllegalStateException("Google OAuth: " + msg);
            }
            return root;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Google OAuth HTTP " + e.getStatusCode().value() + ": " + abbreviate(e.getResponseBodyAsString()),
                    e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Google OAuth: " + e.getMessage(), e);
        }
    }

    private RestClient buildClient(String accessToken) {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5_000);
        rf.setReadTimeout(15_000);
        return rf;
    }

    private String clientId() {
        ApplicationSettings settings = globalSettingsService.get();
        if (settings.getGoogleOauthClientId() == null || settings.getGoogleOauthClientId().isBlank()) {
            throw new IllegalArgumentException("Google OAuth Client-ID fehlt.");
        }
        return settings.getGoogleOauthClientId().trim();
    }

    private String clientSecret() {
        ApplicationSettings settings = globalSettingsService.get();
        if (settings.getGoogleOauthClientSecret() == null || settings.getGoogleOauthClientSecret().isBlank()) {
            throw new IllegalArgumentException("Google OAuth Client-Secret fehlt.");
        }
        return settings.getGoogleOauthClientSecret().trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "…" : value;
    }
}
