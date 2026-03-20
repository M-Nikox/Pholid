/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads sensitive values from Docker secret files and exposes them as Spring
 * properties, allowing containers to use Docker secrets instead of plain-text
 * environment variables.
 *
 * <p>When {@code POSTGRES_PASSWORD_FILE} is set to a readable path (e.g.
 * {@code /run/secrets/postgres_password}), its contents override
 * {@code spring.datasource.password}. If the env var is absent the normal
 * {@code POSTGRES_PASSWORD} fallback in {@code application.properties} is used.
 *
 * <p>When {@code OIDC_CLIENT_SECRET_FILE} is set (e.g.
 * {@code /run/secrets/oidc_client_secret}), its contents are exposed as
 * {@code OIDC_CLIENT_SECRET}, which is referenced by the OAuth2 client
 * registration properties.
 */
public class SecretsEnvironmentPostProcessor implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String POSTGRES_PASSWORD_FILE_ENV = "POSTGRES_PASSWORD_FILE";
    private static final String DATASOURCE_PASSWORD_PROPERTY = "spring.datasource.password";

    private static final String OIDC_SECRET_FILE_ENV = "OIDC_CLIENT_SECRET_FILE";
    private static final String OIDC_SECRET_PROPERTY = "OIDC_CLIENT_SECRET";

    private static final String SMTP_PASSWORD_FILE_ENV = "SMTP_PASSWORD_FILE";
    private static final String SMTP_PASSWORD_PROPERTY = "SMTP_PASSWORD";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        Map<String, Object> secretProperties = new HashMap<>();

        String postgresPasswordFile = environment.getProperty(POSTGRES_PASSWORD_FILE_ENV);
        if (postgresPasswordFile != null) {
            loadSecret(Path.of(postgresPasswordFile), DATASOURCE_PASSWORD_PROPERTY, secretProperties);
        }

        String oidcSecretFile = environment.getProperty(OIDC_SECRET_FILE_ENV);
        if (oidcSecretFile != null) {
            loadSecret(Path.of(oidcSecretFile), OIDC_SECRET_PROPERTY, secretProperties);
        }

        String smtpPasswordFile = environment.getProperty(SMTP_PASSWORD_FILE_ENV);
        if (smtpPasswordFile != null) {
            loadSecret(Path.of(smtpPasswordFile), SMTP_PASSWORD_PROPERTY, secretProperties);
        }

        if (!secretProperties.isEmpty()) {
            // addFirst so these values take precedence over application.properties
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("dockerSecrets", secretProperties));
        }
    }

    private void loadSecret(Path secretPath, String propertyName, Map<String, Object> properties) {
        if (Files.isReadable(secretPath)) {
            try {
                String value = Files.readString(secretPath).strip();
                properties.put(propertyName, value);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to read Docker secret from " + secretPath, e);
            }
        }
    }
}
