package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.AppUserDetailsService;
import de.feuerwehr.manager.security.TotpSessionKeys;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.user.UserService;
import de.feuerwehr.manager.user.UserTotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class LoginTotpController {

    private static final long PENDING_MAX_AGE_MS = Duration.ofMinutes(10).toMillis();

    private final UserRepository userRepository;
    private final UserTotpService userTotpService;
    private final UserService userService;
    private final AppUserDetailsService appUserDetailsService;

    @GetMapping("/login/totp")
    public String totpForm(HttpSession session, Model model) {
        if (!hasValidPending(session)) {
            return "redirect:/login";
        }
        return "login-totp";
    }

    @PostMapping("/login/totp")
    public String verifyTotp(
            @RequestParam String code,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Long userId = pendingUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            if (!userTotpService.verifyLoginCode(userId, code)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Ungültiger Authenticator-Code.");
                return "redirect:/login/totp";
            }
            User user = userRepository.findById(userId).orElseThrow();
            AppUserDetails details = (AppUserDetails) appUserDetailsService.loadUserByUsername(user.getUsername());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
            clearPending(session);
            userService.recordSuccessfulLogin(userId);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/login/totp";
        }
    }

    private static boolean hasValidPending(HttpSession session) {
        return pendingUserId(session) != null;
    }

    private static Long pendingUserId(HttpSession session) {
        Object id = session.getAttribute(TotpSessionKeys.PENDING_USER_ID);
        Object started = session.getAttribute(TotpSessionKeys.PENDING_STARTED_AT);
        if (!(id instanceof Long userId) || !(started instanceof Long startedAt)) {
            return null;
        }
        if (Instant.now().toEpochMilli() - startedAt > PENDING_MAX_AGE_MS) {
            return null;
        }
        return userId;
    }

    private static void clearPending(HttpSession session) {
        session.removeAttribute(TotpSessionKeys.PENDING_USER_ID);
        session.removeAttribute(TotpSessionKeys.PENDING_STARTED_AT);
    }
}
