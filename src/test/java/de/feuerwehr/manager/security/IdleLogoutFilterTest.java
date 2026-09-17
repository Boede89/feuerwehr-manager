package de.feuerwehr.manager.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class IdleLogoutFilterTest {

    @Mock
    private TestModeLogoutHandler testModeLogoutHandler;

    @Mock
    private AuditService auditService;

    private IdleLogoutFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IdleLogoutFilter(testModeLogoutHandler, auditService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsApiAndLogoutPaths() {
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/auth/session"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/logout"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/personal"))).isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/session/keepalive"))).isFalse();
    }

    @Test
    void redirectsWhenIdleExpired() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("GET", "/personal");
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(IdleLogoutSupport.MINUTES_ATTR, 1);
        session.setAttribute(IdleLogoutSupport.LAST_ACTIVITY_ATTR, System.currentTimeMillis() - 120_000L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired=1");
        verify(testModeLogoutHandler).logout(eq(request), eq(response), any());
        verify(auditService).record(eq(AuditEventType.LOGOUT), eq(7L), eq(request));
    }

    @Test
    void returnsJson401ForKeepaliveWhenExpired() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("POST", "/session/keepalive");
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(IdleLogoutSupport.MINUTES_ATTR, 1);
        session.setAttribute(IdleLogoutSupport.LAST_ACTIVITY_ATTR, System.currentTimeMillis() - 120_000L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("expired");
        verify(testModeLogoutHandler).logout(eq(request), eq(response), any());
    }

    @Test
    void continuesAndMarksActivityWhenStillActive() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("GET", "/personal");
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(IdleLogoutSupport.MINUTES_ATTR, 1);
        long previous = System.currentTimeMillis() - 10_000L;
        session.setAttribute(IdleLogoutSupport.LAST_ACTIVITY_ATTR, previous);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(IdleLogoutSupport.lastActivityFrom(session)).isGreaterThan(previous);
        verify(testModeLogoutHandler, never()).logout(any(), any(), any());
    }

    private MockHttpServletRequest authenticatedRequest(String method, String path) {
        MockHttpServletRequest request = request(method, path);
        request.setSession(new MockHttpSession());
        User user = new User();
        user.setId(7L);
        user.setUsername("tester");
        user.setPasswordHash("x");
        user.setDisplayName("Tester");
        user.setRole(UserRole.SUPER_ADMIN);
        user.setActive(true);
        AppUserDetails details = AppUserDetails.from(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, "x", details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request;
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
