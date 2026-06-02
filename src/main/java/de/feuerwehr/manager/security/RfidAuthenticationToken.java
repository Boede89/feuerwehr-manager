package de.feuerwehr.manager.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Anmeldung per RFID-Chip (Lesegerät → API oder später Tastatur-Emulation).
 * Principal vor Auth: normalisierte Chip-ID; danach {@link AppUserDetails}.
 */
public class RfidAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private Object credentials;

    public RfidAuthenticationToken(String cardUid) {
        super(null);
        this.principal = cardUid;
        this.credentials = null;
        setAuthenticated(false);
    }

    public RfidAuthenticationToken(
            AppUserDetails userDetails, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = userDetails;
        this.credentials = null;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
