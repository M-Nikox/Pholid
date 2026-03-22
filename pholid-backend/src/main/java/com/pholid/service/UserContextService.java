/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.config.PholidProperties;
import com.pholid.job.JobRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the current user from the Spring Security context and provides helpers
 * for per-user job isolation: username extraction, admin detection, and ownership checks.
 *
 * <p>When {@code pholid.auth.enabled=false} (default):
 * <ul>
 *   <li>{@link #getCurrentUsername()} returns {@code "anonymous"}</li>
 *   <li>{@link #isAdmin()} returns {@code true} (all access permitted)</li>
 *   <li>Ownership/filter methods allow everything, preserving existing behaviour.</li>
 * </ul>
 */
@Service
public class UserContextService {

    private final PholidProperties props;
    private final JobRepository jobRepository;

    public UserContextService(PholidProperties props, JobRepository jobRepository) {
        this.props         = props;
        this.jobRepository = jobRepository;
    }

    // ── Username ─────────────────────────────────────────────────────────────

    /**
     * Returns the username of the currently authenticated user, or {@code "anonymous"}
     * when auth is disabled or no user is authenticated.
     */
    public String getCurrentUsername() {
        if (!props.auth().enabled()) return "anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return "anonymous";
        }
        if (auth.getPrincipal() instanceof OidcUser oidcUser
                && oidcUser.getPreferredUsername() != null) {
            return oidcUser.getPreferredUsername();
        }
        return auth.getName();
    }

    /**
     * Returns the user's full name from the OIDC {@code name} claim, falling back
     * to the username if the claim is absent (or when auth is disabled).
     */
    public String getFullName() {
        if (!props.auth().enabled()) return "Anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
            String full = oidcUser.getFullName();
            if (full != null && !full.isBlank()) return full;
        }
        return getCurrentUsername();
    }

    /**
     * Returns the user's email from the OIDC token, or {@code null} if not
     * available (auth disabled, or email scope not granted).
     */
    public String getEmail() {
        if (!props.auth().enabled()) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getEmail();
        }
        return null;
    }

    // ── Admin check ──────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the current user is an admin (or auth is disabled).
     * Admin status is determined by:
     * <ol>
     *   <li>The {@code groups} claim in the OIDC token containing the configured
     *       {@code pholid.auth.admin-group} value.</li>
     *   <li>A {@code ROLE_ADMIN} Spring Security granted authority.</li>
     * </ol>
     */
    public boolean isAdmin() {
        if (!props.auth().enabled()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;

        // Check OIDC groups claim — requires the groups mapper on the Keycloak client scope
        if (auth.getPrincipal() instanceof OidcUser oidcUser) {
            Object groupsClaim = oidcUser.getClaim("groups");
            if (groupsClaim instanceof List<?> groups
                    && groups.contains(props.auth().adminGroup())) {
                return true;
            }
        }
        // Fallback: check configured admin role name
        String configuredRole = props.auth().adminRole();
        if (configuredRole != null && !configuredRole.isBlank()) {
            if (auth.getAuthorities().stream()
                    .anyMatch(a -> ("ROLE_" + configuredRole).equals(a.getAuthority())
                            || configuredRole.equals(a.getAuthority()))) {
                return true;
            }
        }
        // Fallback: Spring Security ROLE_ADMIN granted authority
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    // ── Ownership checks ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the current user may access the job identified by the
     * given Flamenco job ID (auth disabled → always true; admin → always true;
     * otherwise checks that the user submitted this job).
     */
    public boolean canAccessByFlamencoId(String flamencoJobId) {
        if (!props.auth().enabled()) return true;
        if (isAdmin()) return true;
        return jobRepository.existsByFlamencoJobIdAndSubmittedBy(
                flamencoJobId, getCurrentUsername());
    }

    /**
     * Returns {@code true} if the current user may access the job identified by the
     * given Pholid job ID (the 16-char hex filesystem directory name).
     */
    public boolean canAccessByPholidId(String pholidJobId) {
        if (!props.auth().enabled()) return true;
        if (isAdmin()) return true;
        return jobRepository.existsByPholidJobIdAndSubmittedBy(
                pholidJobId, getCurrentUsername());
    }

    // ── Job list filtering ───────────────────────────────────────────────────

    /**
     * Filters a Flamenco job-list response so that non-admin users only see their own jobs.
     * The response map is expected to contain a {@code "jobs"} key with a list of job maps,
     * each having an {@code "id"} key matching the Flamenco job UUID.
     *
     * <p>When auth is disabled or the current user is an admin, the original response
     * is returned unchanged.
     */
    public Map<String, Object> filterJobsForCurrentUser(Map<String, Object> flamencoResponse) {
        if (flamencoResponse == null) {
            return Map.of("jobs", List.of());
        }
        if (!props.auth().enabled() || isAdmin()) return flamencoResponse;

        String username = getCurrentUsername();
        Set<String> ownedIds = jobRepository.findFlamencoJobIdsBySubmittedBy(username);

        if (!(flamencoResponse.get("jobs") instanceof List<?> allJobs)) {
            return flamencoResponse;
        }

        List<?> filtered = allJobs.stream()
                .filter(j -> j instanceof Map<?, ?> job && ownedIds.contains(job.get("id")))
                .toList();

        Map<String, Object> result = new java.util.HashMap<>(flamencoResponse);
        result.put("jobs", filtered);
        return result;
    }
}
