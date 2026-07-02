package de.feuerwehr.manager.config;

import de.feuerwehr.manager.security.SecurityProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionTimeoutConfig {

    @Bean
    public ServletContextInitializer sessionTimeoutInitializer(SecurityProperties securityProperties) {
        return servletContext -> servletContext.setSessionTimeout(securityProperties.sessionTimeoutMinutes());
    }
}
