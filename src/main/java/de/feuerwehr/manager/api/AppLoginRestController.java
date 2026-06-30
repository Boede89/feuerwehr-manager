package de.feuerwehr.manager.api;

import de.feuerwehr.manager.security.AppUserDetails;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON-Login für die Android Einsatz-App (Session-Cookie). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AppLoginRestController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (body == null
                || body.username() == null
                || body.username().isBlank()
                || body.password() == null
                || body.password().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Benutzername und Passwort sind erforderlich"));
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.username().trim(), body.password()));
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
                        "message", "Zweiter Faktor erforderlich — bitte in der Web-Oberfläche anmelden."));
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
                    "unitId", details.getUnitId() != null ? details.getUnitId() : 0));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Benutzername oder Passwort ist falsch"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Anmeldung fehlgeschlagen"));
        }
    }
}
