/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.service;

import com.pangolin.client.FlamencoClient;
import com.pangolin.job.Job;
import com.pangolin.job.JobRepository;
import com.pangolin.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Periodically polls the Flamenco Manager to update job progress and status
 * for all active (non-terminal) jobs in the database.
 *
 * <p>Progress is calculated as the percentage of completed tasks out of total tasks
 * for a job. When a job reaches a terminal status (completed / failed), the
 * {@link NotificationService} is triggered to deliver email/webhook notifications.
 */
@Service
public class JobProgressService {

    private static final Logger log = LoggerFactory.getLogger(JobProgressService.class);

    private final JobRepository jobRepository;
    private final FlamencoClient flamencoClient;
    private final NotificationService notificationService;

    public JobProgressService(JobRepository jobRepository,
                               FlamencoClient flamencoClient,
                               NotificationService notificationService) {
        this.jobRepository       = jobRepository;
        this.flamencoClient      = flamencoClient;
        this.notificationService = notificationService;
    }

    /**
     * Runs every 30 seconds. Fetches the current status and task summary from Flamenco
     * for each active job and updates the local {@code progress} field.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void updateJobProgress() {
        List<Job> activeJobs = jobRepository.findActiveJobsWithFlamencoId();
        if (activeJobs.isEmpty()) return;

        log.debug("Polling progress for {} active job(s)", activeJobs.size());

        for (Job job : activeJobs) {
            try {
                updateSingleJob(job);
            } catch (Exception e) {
                log.warn("Failed to update progress for job {}: {}", job.getFlamencoJobId(), e.getMessage());
            }
        }
    }

    private void updateSingleJob(Job job) {
        Map<String, Object> flamencoJob = flamencoClient.getJob(job.getFlamencoJobId());
        if (flamencoJob == null) return;

        String newStatus = (String) flamencoJob.get("status");
        int progress = calculateProgress(job.getFlamencoJobId(), flamencoJob);

        boolean statusChanged = newStatus != null && !newStatus.equals(job.getStatus());
        boolean progressChanged = progress != job.getProgress();

        if (!statusChanged && !progressChanged) return;

        if (progressChanged) job.setProgress(progress);

        if (statusChanged) {
            String oldStatus = job.getStatus();
            job.setStatus(newStatus);
            log.info("Job {} status changed: {} → {} (progress: {}%)",
                    job.getFlamencoJobId(), oldStatus, newStatus, progress);

            // Set completion timestamp on terminal status
            if (isTerminal(newStatus) && job.getCompletedAt() == null) {
                job.setCompletedAt(OffsetDateTime.now());
            }

            // Trigger notifications when job reaches completed or failed
            if ("completed".equals(newStatus) || "failed".equals(newStatus)) {
                jobRepository.save(job);
                notificationService.notifyJobStatus(job, newStatus);
                return;
            }
        }

        jobRepository.save(job);
    }

    /**
     * Calculates progress (0-100) from the tasks summary of a Flamenco job response.
     * Falls back to status-based heuristic if task data is unavailable.
     */
    @SuppressWarnings("unchecked")
    private int calculateProgress(String flamencoJobId, Map<String, Object> flamencoJob) {
        try {
            // Try to get task summary from the job response
            Object taskSummary = flamencoJob.get("task_progress_percentage");
            if (taskSummary instanceof Number n) {
                return Math.min(100, Math.max(0, n.intValue()));
            }

            // Fall back to fetching tasks list
            Map<String, Object> tasksResponse = flamencoClient.getJobTasks(flamencoJobId);
            if (tasksResponse == null) return statusToProgress((String) flamencoJob.get("status"));

            List<?> tasks = (List<?>) tasksResponse.get("tasks");
            if (tasks == null || tasks.isEmpty()) return statusToProgress((String) flamencoJob.get("status"));

            long total = tasks.size();
            long done = tasks.stream()
                    .filter(t -> t instanceof Map<?, ?> m
                            && "completed".equals(m.get("status")))
                    .count();

            return total > 0 ? (int) (done * 100L / total) : 0;

        } catch (Exception e) {
            log.debug("Could not calculate progress for job {}: {}", flamencoJobId, e.getMessage());
            return statusToProgress((String) flamencoJob.get("status"));
        }
    }

    private int statusToProgress(String status) {
        if (status == null) return 0;
        return switch (status) {
            case "completed" -> 100;
            case "active", "requeueing" -> 50;
            case "queued", "under-construction" -> 0;
            default -> 0;
        };
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status) || "canceled".equals(status);
    }
}
