package de.feuerwehr.manager.api;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.RfidAuthenticationToken;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.security.TotpSessionKeys;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RfidAuthRestController {

    private final AuthenticationManager authenticationManager;
    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public record RfidLoginRequest(String cardUid) {}

    @PostMapping("/rfid")
    public ResponseEntity<Map<String, Object>> loginByRfid(
            @RequestBody RfidLoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!securityProperties.rfidApiEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "RFID-Anmeldung ist deaktiviert"));
        }
        if (body == null || body.cardUid() == null || body.cardUid().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Chip-ID fehlt"));
        }
        try {
            Authentication auth = authenticationManager.authenticate(new RfidAuthenticationToken(body.cardUid()));
            AppUserDetails details = (AppUserDetails) auth.getPrincipal();
            User user = userRepository.findById(details.getUserId()).orElseThrow();
            if (user.isTotpEnabled() && user.getTotpSecret() != null && !user.getTotpSecret().isBlank()) {
                HttpSession session = request.getSession();
                session.setAttribute(TotpSessionKeys.PENDING_USER_ID, details.getUserId());
                session.setAttribute(TotpSessionKeys.PENDING_STARTED_AT, Instant.now().toEpochMilli());
                SecurityContextHolder.clearContext();
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "totpRequired", true,
                        "userId", details.getUserId(),
                        "displayName", details.getDisplayName(),
                        "redirectUrl", "/login/totp"));
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            userService.recordSuccessfulLogin(details.getUserId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "totpRequired", false,
                    "userId", details.getUserId(),
                    "displayName", details.getDisplayName(),
                    "redirectUrl", "/"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Anmeldung fehlgeschlagen"));
        }
    }
}
