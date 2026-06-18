package de.feuerwehr.manager.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionCookieSecureFilterTest {

    @Test
    void addsSecureOnHttps() {
        String raw = "JSESSIONID=abc; Path=/; HttpOnly; SameSite=Lax";
        assertThat(SessionCookieSecureFilter.adjustSessionCookie(raw, true))
                .isEqualTo("JSESSIONID=abc; Path=/; HttpOnly; SameSite=Lax; Secure");
    }

    @Test
    void stripsSecureOnHttp() {
        String raw = "JSESSIONID=abc; Path=/; Secure; HttpOnly; SameSite=Lax";
        assertThat(SessionCookieSecureFilter.adjustSessionCookie(raw, false))
                .isEqualTo("JSESSIONID=abc; Path=/; HttpOnly; SameSite=Lax");
    }

    @Test
    void leavesOtherCookiesUntouched() {
        String raw = "XSRF-TOKEN=token; Path=/; Secure";
        assertThat(SessionCookieSecureFilter.adjustSessionCookie(raw, false)).isEqualTo(raw);
    }
}
