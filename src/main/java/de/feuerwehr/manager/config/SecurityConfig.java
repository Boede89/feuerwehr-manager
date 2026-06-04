package de.feuerwehr.manager.config;

import de.feuerwehr.manager.dsgvo.DsgvoProperties;
import de.feuerwehr.manager.security.AppUserDetailsService;
import de.feuerwehr.manager.security.AuditLogoutSuccessHandler;
import de.feuerwehr.manager.security.TestModeLogoutHandler;
import de.feuerwehr.manager.security.RfidAuthenticationProvider;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.user.UserService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, DsgvoProperties.class})
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RfidAuthenticationProvider rfidAuthenticationProvider(UserService userService) {
        return new RfidAuthenticationProvider(userService);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            RfidAuthenticationProvider rfidAuthenticationProvider) {
        DaoAuthenticationProvider dao = new DaoAuthenticationProvider();
        dao.setUserDetailsService(userDetailsService);
        dao.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(dao, rfidAuthenticationProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            AuditLogoutSuccessHandler auditLogoutSuccessHandler,
            TestModeLogoutHandler testModeLogoutHandler)
            throws Exception {
        http.authenticationManager(authenticationManager);
        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .sessionManagement(session -> session
                        .sessionFixation(sf -> sf.changeSessionId())
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/privacy/**",
                                "/css/**",
                                "/js/**",
                                "/actuator/health",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/rfid")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhook/**")
                        .permitAll()
                        .requestMatchers("/admin/global/**")
                        .hasRole("SUPER_ADMIN")
                        .requestMatchers("/admin/**")
                        .hasAnyRole("SUPER_ADMIN", "UNIT_ADMIN")
                        .requestMatchers("/settings/users/**", "/settings/divera/**")
                        .hasAnyRole("SUPER_ADMIN", "UNIT_ADMIN")
                        .requestMatchers("/admin/test-mode", "/settings/test-mode/**")
                        .hasRole("SUPER_ADMIN")
                        .requestMatchers("/settings/units/**")
                        .hasRole("SUPER_ADMIN")
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(testModeLogoutHandler)
                        .logoutSuccessHandler(auditLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "font-src 'self'; "
                                        + "connect-src 'self'; "
                                        + "frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
