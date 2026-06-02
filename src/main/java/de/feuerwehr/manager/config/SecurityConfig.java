package de.feuerwehr.manager.config;

import de.feuerwehr.manager.dsgvo.DsgvoProperties;
import de.feuerwehr.manager.security.AppUserDetailsService;
import de.feuerwehr.manager.security.AuditLogoutSuccessHandler;
import de.feuerwehr.manager.security.PrivacyConsentFilter;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
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
            PrivacyConsentFilter privacyConsentFilter,
            AuditLogoutSuccessHandler auditLogoutSuccessHandler)
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
                                "/actuator/health",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/rfid")
                        .permitAll()
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
                        .logoutSuccessHandler(auditLogoutSuccessHandler)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.sameOrigin()));

        http.addFilterAfter(privacyConsentFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
