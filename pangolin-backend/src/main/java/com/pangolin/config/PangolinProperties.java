/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds all pangolin.* properties from application.properties into a single typed record.
 * Registered automatically via @ConfigurationPropertiesScan on RenderApplication.
 *
 * SMTP (mail) is intentionally omitted for v2 — add back in v3 if notification emails
 * are needed. The NotificationService already handles a missing mail configuration
 * gracefully via Optional<JavaMailSender>.
 */
@ConfigurationProperties("pangolin")
public record PangolinProperties(
        Manager manager,
        Storage storage,
        Frames frames,
        Download download,
        ProjectName projectName,
        File file,
        Http http,
        Delete delete,
        Zip zip,
        Auth auth,
        Webhook webhook,
        Quota quota
) {
    public record Manager(String url) {}
    public record Storage(String root) {}
    public record Frames(int limit, int cap) {}
    public record Download(int maxFiles) {}
    public record ProjectName(int maxLength) {}
    public record File(long maxSizeMb) {}
    public record Http(int connectTimeout, int readTimeout) {}
    public record Delete(boolean enabled) {}
    public record Zip(long maxUncompressedMb, int maxEntries) {}

    /**
     * Auth config.
     * adminGroup: the Keycloak group name that grants admin access (e.g. "pangolin-admins").
     *             Requires the "groups" mapper on the Keycloak client scope.
     * adminRole:  fallback Spring Security role name (e.g. "ADMIN").
     * logoutUri:  Keycloak's end_session endpoint — browser-facing URL used to terminate
     *             the Keycloak session on logout. Must be reachable by the user's browser.
     */
    public record Auth(boolean enabled, String adminGroup, String adminRole, String logoutUri) {}

    public record Webhook(String url) {}
    public record Quota(int maxConcurrentJobs, int maxSubmissionsPerHour) {}
}
