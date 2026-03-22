/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.client.FlamencoClient;
import com.pholid.job.Job;
import com.pholid.job.JobRepository;
import com.pholid.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Background scheduler that polls Flamenco for the status and progress of
 * active jobs and updates the Pholid database accordingly.
 *
 * <p>Without this, jobs are saved to the DB on submission with status "queued"
 * and never updated — the history panel would always be empty and progress
 * would always read 0.
 *
 * <p>Only jobs that have a Flamenco job ID are polled (i.e. jobs that were
 * successfully submitted to Flamenco). Jobs that failed before reaching
 * Flamenco are ignored.
 *
 * <p>On every poll:
 * <ul>
 *   <li>Progress is calculated from Flamenco's steps_completed / steps_total
 *       and written back to the DB.</li>
 *   <li>Status changes are tracked and saved.</li>
 *   <li>When a job reaches a terminal status (completed, failed, canceled),
 *       completedAt is set and NotificationService is called.</li>
 * </ul>
 *
 * <p>Flamenco connectivity failures are caught and logged — a transient network
 * blip will not crash the scheduler or affect the application.
 */
@Service
public class JobStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(JobStatusSyncService.class);

    private static final Set<String> TERMINAL_STATUSES = Set.of("completed", "failed", "canceled");

    private final JobRepository       jobRepository;
    private final FlamencoClient      flamencoClient;
    private final NotificationService notificationService;

    public JobStatusSyncService(JobRepository jobRepository,
                                FlamencoClient flamencoClient,
                                NotificationService notificationService) {
        this.jobRepository       = jobRepository;
        this.flamencoClient      = flamencoClient;
        this.notificationService = notificationService;
    }

    /**
     * Polls Flamenco every 3 seconds for the status and progress of all active
     * jobs — matches the active jobs polling interval in active-sessions.js.
     * Fixed delay (not fixed rate) so runs don't overlap if a poll takes longer
     * than expected.
     */
    @Scheduled(fixedDelay = 3000)
    public void syncActiveJobStatuses() {
        List<Job> activeJobs = jobRepository.findActiveJobsWithFlamencoId();

        if (activeJobs.isEmpty()) return;

        log.debug("Syncing status for {} active job(s)", activeJobs.size());

        for (Job job : activeJobs) {
            syncJob(job);
        }
    }

    private void syncJob(Job job) {
        try {
            Map<String, Object> flamencoJob = flamencoClient.getJob(job.getFlamencoJobId());

            if (flamencoJob == null) {
                log.warn("Flamenco returned null for job {} — marking as failed in DB", job.getFlamencoJobId());
                job.setStatus("failed");
                job.setCompletedAt(OffsetDateTime.now());
                jobRepository.save(job);
                return;
            }

            String newStatus = (String) flamencoJob.get("status");
            int newProgress  = calculateProgress(flamencoJob);

            boolean statusChanged   = newStatus != null && !newStatus.equals(job.getStatus());
            boolean progressChanged = newProgress != job.getProgress();

            if (!statusChanged && !progressChanged) return;

            if (statusChanged) {
                log.info("Job {} status changed: {} → {}",
                        job.getFlamencoJobId(), job.getStatus(), newStatus);
                job.setStatus(newStatus);
            }

            if (progressChanged) {
                job.setProgress(newProgress);
            }

            if (statusChanged && TERMINAL_STATUSES.contains(newStatus)) {
                job.setProgress(100); // ensure 100% on completion
                job.setCompletedAt(OffsetDateTime.now());
                jobRepository.save(job);
                notificationService.notifyJobStatus(job, newStatus);
                log.info("Job {} ({}) marked as {} in DB",
                        job.getPholidJobId(), job.getFlamencoJobId(), newStatus);
            } else {
                jobRepository.save(job);
            }

        } catch (ResourceAccessException e) {
            log.warn("Could not reach Flamenco Manager during job sync: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error syncing job {}", job.getFlamencoJobId(), e);
        }
    }

    /**
     * Calculates progress percentage from Flamenco's steps_completed / steps_total.
     * Returns 0 if either field is missing or steps_total is zero.
     */
    private int calculateProgress(Map<String, Object> flamencoJob) {
        try {
            Object completed = flamencoJob.get("steps_completed");
            Object total     = flamencoJob.get("steps_total");

            if (completed == null || total == null) return 0;

            int stepsCompleted = ((Number) completed).intValue();
            int stepsTotal     = ((Number) total).intValue();

            if (stepsTotal <= 0) return 0;

            return (int) Math.round((stepsCompleted / (double) stepsTotal) * 100);
        } catch (Exception e) {
            log.debug("Could not calculate progress from Flamenco response: {}", e.getMessage());
            return 0;
        }
    }
}
