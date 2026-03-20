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
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Spring Security configuration controlled by the {@code pangolin.auth.enabled} property.
 *
 * <ul>
 *   <li>When {@code pangolin.auth.enabled=false} (default): all requests are permitted and
 *       OIDC auto-configuration is suppressed so the app starts without Keycloak running.</li>
 *   <li>When {@code pangolin.auth.enabled=true}: OAuth2/OIDC login is required for all
 *       endpoints except health checks and static resources. A successful login is recorded
 *       in the audit log.</li>
 * </ul>
 *
 * Logout uses a direct redirect to Keycloak's end_session endpoint configured via
 * pangolin.auth.logout-uri. This avoids OIDC discovery (issuer-uri) which fails in
 * Docker Compose when the internal and external Keycloak hostnames differ.
 *
 * Keycloak note: preferred_username is populated by default in Keycloak's standard token.
 * The groups claim requires the "groups" mapper to be added to the client scope in Keycloak
 * (Client scopes -> Add mapper -> Group Membership, token claim name: groups).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    // ── Auth DISABLED (default) ──────────────────────────────────────────────

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
         * and avoids HTTP calls to the OIDC discovery endpoint at startup.
         */
        @Bean
        public ClientRegistrationRepository noOpClientRegistrationRepository() {
            return registrationId -> null;
        }
    }

    // ── Auth ENABLED ─────────────────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "pangolin.auth.enabled", havingValue = "true")
    static class SecuredConfig {

        @Bean
        public SecurityFilterChain securedFilterChain(HttpSecurity http,
                AuditLogService auditLogService,
                PangolinProperties props) throws Exception {
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
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(props)))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
            return http.build();
        }

        /**
         * Builds a redirect to Keycloak's end_session endpoint directly, bypassing
         * OidcClientInitiatedLogoutSuccessHandler which requires issuer-uri discovery.
         * The logout URI must be browser-reachable (localhost in dev, public domain in prod).
         */
        private LogoutSuccessHandler oidcLogoutSuccessHandler(PangolinProperties props) {
            return (request, response, authentication) -> {
                String postLogoutRedirect = request.getScheme() + "://"
                        + request.getServerName()
                        + (request.getServerPort() == 80 || request.getServerPort() == 443
                                ? "" : ":" + request.getServerPort())
                        + "/";

                String logoutUrl = UriComponentsBuilder
                        .fromUriString(props.auth().logoutUri())
                        .queryParam("post_logout_redirect_uri", postLogoutRedirect)
                        .queryParam("client_id", "pangolin")
                        .toUriString();

                response.sendRedirect(logoutUrl);
            };
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
