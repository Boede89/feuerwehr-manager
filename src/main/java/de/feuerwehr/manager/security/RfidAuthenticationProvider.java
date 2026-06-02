package de.feuerwehr.manager.security;

import de.feuerwehr.manager.user.RfidCardUidNormalizer;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Nur über {@link de.feuerwehr.manager.config.SecurityConfig} als Bean registrieren –
 * nicht als {@code @Component}, sonst überschreibt Spring den Passwort-Login.
 */
public class RfidAuthenticationProvider implements AuthenticationProvider {

    private final UserService userService;

    public RfidAuthenticationProvider(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String rawUid = (String) authentication.getPrincipal();
        String normalized = RfidCardUidNormalizer.normalize(rawUid);
        if (!RfidCardUidNormalizer.isValid(normalized)) {
            throw new BadCredentialsException("Ungültige Chip-ID");
        }
        User user = userService.findActiveByRfidCardUid(normalized)
                .filter(u -> u.getAnonymizedAt() == null)
                .orElseThrow(() -> new BadCredentialsException("Chip nicht registriert"));
        AppUserDetails details = AppUserDetails.from(user);
        return new RfidAuthenticationToken(details, details.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return RfidAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
