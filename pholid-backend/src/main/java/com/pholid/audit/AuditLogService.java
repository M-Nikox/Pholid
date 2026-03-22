/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Service for recording audit log entries.
 *
 * <p>The current username is resolved automatically from the Spring Security context.
 * When authentication is disabled (or no user is authenticated), {@code "anonymous"}
 * is used as the username.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an audit event.
     *
     * @param action       the action performed (e.g. "JOB_SUBMITTED")
     * @param resourceType the type of resource affected (e.g. "JOB")
     * @param resourceId   the identifier of the affected resource (may be {@code null})
     * @param username     the user performing the action (pass {@code null} to resolve
     *                     automatically from the security context)
     * @param details      optional free-text details (may be {@code null})
     */
    public void logAction(String action, String resourceType, String resourceId,
                          String username, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId);
            entry.setUsername(username != null ? username : resolveUsername());
            entry.setTimestamp(OffsetDateTime.now());
            entry.setDetails(details);
            repository.save(entry);
        } catch (Exception e) {
            // Audit logging must never break the main flow
            log.warn("Failed to write audit log entry [{} {} {}]: {}", action, resourceType, resourceId, e.getMessage());
        }
    }

    /**
     * Convenience overload that resolves the username from the security context.
     */
    public void logAction(String action, String resourceType, String resourceId, String details) {
        logAction(action, resourceType, resourceId, null, details);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }
}
