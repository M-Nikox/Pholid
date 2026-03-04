/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
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
 */
public class SecretsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String POSTGRES_PASSWORD_FILE_ENV = "POSTGRES_PASSWORD_FILE";
    private static final String DATASOURCE_PASSWORD_PROPERTY = "spring.datasource.password";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Map<String, Object> secretProperties = new HashMap<>();

        String postgresPasswordFile = environment.getProperty(POSTGRES_PASSWORD_FILE_ENV);
        if (postgresPasswordFile != null) {
            loadSecret(Path.of(postgresPasswordFile), DATASOURCE_PASSWORD_PROPERTY, secretProperties);
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
