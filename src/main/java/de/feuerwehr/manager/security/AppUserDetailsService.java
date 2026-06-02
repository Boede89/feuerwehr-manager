package de.feuerwehr.manager.security;

import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService
                .findActiveForLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden"));
        if (user.getRole().isUnitAdmin() && user.getUnit() == null) {
            throw new UsernameNotFoundException("Einheitsadmin ohne zugeordnete Einheit");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException("Kein Passwort hinterlegt – nur Chip-Anmeldung möglich");
        }
        return AppUserDetails.from(user);
    }
}
