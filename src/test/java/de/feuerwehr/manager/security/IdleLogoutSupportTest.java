package de.feuerwehr.manager.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class IdleLogoutSupportTest {

    @Test
    void oneMinuteExpiresAtSixtySeconds() {
        long start = 1_000_000L;
        assertThat(IdleLogoutSupport.isExpired(1, start, start + 59_999L)).isFalse();
        assertThat(IdleLogoutSupport.isExpired(1, start, start + 60_000L)).isTrue();
    }

    @Test
    void zeroOrMissingConfigNeverExpires() {
        assertThat(IdleLogoutSupport.isExpired(0, 1L, 1_000_000L)).isFalse();
        assertThat(IdleLogoutSupport.isExpired(null, 1L, 1_000_000L)).isFalse();
        assertThat(IdleLogoutSupport.isExpired(1, null, 1_000_000L)).isFalse();
    }

    @Test
    void storeConfigStartsClockOnce() {
        MockHttpSession session = new MockHttpSession();
        IdleLogoutSupport.storeConfig(session, 1);
        Long first = IdleLogoutSupport.lastActivityFrom(session);
        assertThat(IdleLogoutSupport.minutesFrom(session)).isEqualTo(1);
        assertThat(first).isNotNull();

        session.setAttribute(IdleLogoutSupport.LAST_ACTIVITY_ATTR, 42L);
        IdleLogoutSupport.storeConfig(session, 1);
        assertThat(IdleLogoutSupport.lastActivityFrom(session)).isEqualTo(42L);
    }

    @Test
    void disablingClearsLastActivity() {
        MockHttpSession session = new MockHttpSession();
        IdleLogoutSupport.storeConfig(session, 5);
        IdleLogoutSupport.storeConfig(session, 0);
        assertThat(IdleLogoutSupport.minutesFrom(session)).isEqualTo(0);
        assertThat(IdleLogoutSupport.lastActivityFrom(session)).isNull();
    }
}
