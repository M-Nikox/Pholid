/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.service;

import com.pangolin.config.PangolinProperties;
import com.pangolin.job.JobRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Enforces per-user (or global when auth is disabled) submission quotas:
 * <ul>
 *   <li>Maximum concurrent active jobs per user.</li>
 *   <li>Maximum job submissions per hour per user.</li>
 * </ul>
 *
 * <p>Admins are exempt from all quota checks.
 * When auth is disabled, quotas are applied globally using the {@code "anonymous"} username.
 */
@Service
public class QuotaService {

    private final PangolinProperties props;
    private final JobRepository jobRepository;
    private final UserContextService userContextService;

    public QuotaService(PangolinProperties props,
                        JobRepository jobRepository,
                        UserContextService userContextService) {
        this.props              = props;
        this.jobRepository      = jobRepository;
        this.userContextService = userContextService;
    }

    /**
     * Checks whether the given user may submit a new job.
     *
     * @param username the username to check (use {@code "anonymous"} when auth is disabled)
     * @throws QuotaExceededException if any quota limit is exceeded
     */
    public void checkQuota(String username) {
        if (userContextService.isAdmin()) return;

        int maxConcurrent = props.quota().maxConcurrentJobs();
        int maxPerHour    = props.quota().maxSubmissionsPerHour();

        long concurrent = jobRepository.findActiveJobsBySubmittedBy(username).size();
        if (concurrent >= maxConcurrent) {
            throw new QuotaExceededException(
                    "You have reached the maximum number of concurrent active jobs (" + maxConcurrent + "). "
                    + "Wait for a job to complete before submitting another.");
        }

        long recent = jobRepository.countJobsBySubmittedBySince(
                username, OffsetDateTime.now().minusHours(1));
        if (recent >= maxPerHour) {
            throw new QuotaExceededException(
                    "You have exceeded the maximum number of job submissions per hour (" + maxPerHour + "). "
                    + "Please wait before submitting again.");
        }
    }

    /**
     * Exception thrown when a quota limit is exceeded.
     */
    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}
