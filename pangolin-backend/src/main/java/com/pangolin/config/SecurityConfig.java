/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.config;

import com.pangolin.audit.AuditLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Spring Security configuration controlled by the {@code pangolin.auth.enabled} property.
 *
 * <ul>
 *   <li>When {@code pangolin.auth.enabled=false} (default): all requests are permitted and
 *       OIDC auto-configuration is suppressed so the app starts without Authentik running.</li>
 *   <li>When {@code pangolin.auth.enabled=true}: OAuth2/OIDC login is required for all
 *       endpoints except health checks and static resources. A successful login is recorded
 *       in the audit log.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    // ── Auth DISABLED (default) ──────────────────────────────────────────────

    /**
     * Open security configuration used when authentication is disabled.
     * Permits all requests and provides a no-op {@link ClientRegistrationRepository}
     * so that Spring Boot's OAuth2 auto-configuration does not attempt OIDC discovery
     * at startup (which would fail if Authentik is not running).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "pangolin.auth.enabled", havingValue = "false", matchIfMissing = true)
    static class OpenConfig {

        @Bean
        public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }

        /**
         * No-op repository that prevents OAuth2 auto-configuration from running
         * (and thus avoids HTTP calls to the OIDC discovery endpoint at startup).
         */
        @Bean
        public ClientRegistrationRepository noOpClientRegistrationRepository() {
            return registrationId -> null;
        }
    }

    // ── Auth ENABLED ─────────────────────────────────────────────────────────

    /**
     * Secured configuration used when authentication is enabled.
     * Requires OAuth2/OIDC login for all endpoints except health checks and
     * static resources.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "pangolin.auth.enabled", havingValue = "true")
    static class SecuredConfig {

        @Bean
        public SecurityFilterChain securedFilterChain(HttpSecurity http,
                AuditLogService auditLogService) throws Exception {
            http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/css/**", "/js/**", "/svg/**",
                                "/error", "/error/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oidcLoginSuccessHandler(auditLogService)))
                // CSRF is kept enabled for the login/UI flow.
                // REST API endpoints under /api/** are used with Bearer tokens (not cookies)
                // and therefore do not require CSRF protection.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
            return http.build();
        }

        private AuthenticationSuccessHandler oidcLoginSuccessHandler(AuditLogService auditLogService) {
            return (request, response, authentication) -> {
                String username = authentication.getName();
                String details = null;
                if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
                    if (oidcUser.getPreferredUsername() != null) {
                        username = oidcUser.getPreferredUsername();
                    }
                    if (oidcUser.getEmail() != null) {
                        details = "email=" + oidcUser.getEmail();
                    }
                }
                auditLogService.logAction("USER_LOGIN", "USER", username, username, details);
                response.sendRedirect(request.getContextPath() + "/");
            };
        }
    }
}
