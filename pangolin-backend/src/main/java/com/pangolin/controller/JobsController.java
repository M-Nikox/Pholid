/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.controller;

import com.pangolin.client.FlamencoClient;
import com.pangolin.dto.JobSetStatusRequest;
import com.pangolin.dto.TaskLogMeta;
import com.pangolin.exception.ValidationException;
import com.pangolin.job.Job;
import com.pangolin.job.JobRepository;
import com.pangolin.service.UserContextService;
import com.pangolin.validation.IdValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job query, cancel, and task log endpoints.
 *
 * Active jobs (/active) → Flamenco passthrough, filtered by user.
 *   Flamenco data needed here for live progress, ETA, steps_completed etc.
 *
 * Job history (/history) → Pangolin DB, paginated correctly per user.
 *   Completed/failed/canceled jobs don't need live Flamenco data, and
 *   querying our own DB means pagination is accurate regardless of how
 *   many other users' jobs exist in Flamenco.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobsController {

    private static final Logger log = LoggerFactory.getLogger(JobsController.class);

    private static final String ACTIVE_STATUSES =
            "active,queued,paused,pause-requested,cancel-requested,under-construction,requeueing";

    private final FlamencoClient flamencoClient;
    private final RestClient restClient;
    private final UserContextService userContextService;
    private final JobRepository jobRepository;

    public JobsController(FlamencoClient flamencoClient, RestClient restClient,
                          UserContextService userContextService, JobRepository jobRepository) {
        this.flamencoClient     = flamencoClient;
        this.restClient         = restClient;
        this.userContextService = userContextService;
        this.jobRepository      = jobRepository;
    }

    // ── Active jobs (Flamenco passthrough, filtered) ────────────────────────

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveJobs() {
        Map<String, Object> body = flamencoClient.getJobs(ACTIVE_STATUSES, null, null);
        if (body == null) {
            return ResponseEntity.ok(Map.of("jobs", List.of()));
        }
        body = userContextService.filterJobsForCurrentUser(body);
        log.info("Fetched active jobs. Count: {}", jobCount(body));
        return ResponseEntity.ok(body);
    }

    // ── Job history (DB-backed, correctly paginated per user) ───────────────

    /**
     * Returns paginated terminal job history (completed/failed/canceled) from
     * the Pangolin database. Admins see all jobs; regular users see only their own.
     *
     * Response shape is intentionally identical to the Flamenco /jobs response
     * so the frontend JS requires no changes.
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getJobHistory(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        if (limit < 1 || limit > 200) throw new ValidationException("Limit must be between 1 and 200");
        if (offset < 0)               throw new ValidationException("Offset must be >= 0");

        int pageNumber = (limit > 0) ? offset / limit : 0;
        PageRequest pageRequest = PageRequest.of(pageNumber, limit);

        Page<Job> page;
        if (userContextService.isAdmin()) {
            page = jobRepository.findAllHistory(pageRequest);
            log.info("Fetched history (admin): {} jobs (page {}/{})",
                    page.getNumberOfElements(), pageNumber, page.getTotalPages());
        } else {
            String username = userContextService.getCurrentUsername();
            page = jobRepository.findHistoryByUser(username, pageRequest);
            log.info("Fetched history for {}: {} jobs (page {}/{})",
                    username, page.getNumberOfElements(), pageNumber, page.getTotalPages());
        }

        List<Map<String, Object>> jobs = page.getContent().stream()
                .map(this::toFlamencoShape)
                .toList();

        return ResponseEntity.ok(Map.of("jobs", jobs));
    }

    /**
     * Maps a Pangolin Job entity to the same shape the Flamenco API returns,
     * so the frontend JS works without modification.
     *
     * Fields the history panel uses:
     *   job.id                          → Flamenco job UUID (log modal, delete button)
     *   job.metadata['pangolin.job_id'] → Pangolin job ID  (download link)
     *   job.name                        → display name
     *   job.status                      → colour/label
     */
    private Map<String, Object> toFlamencoShape(Job job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",       job.getFlamencoJobId());
        map.put("name",     job.getName());
        map.put("status",   job.getStatus());
        map.put("metadata", Map.of(
                "pangolin.job_id", job.getPangolinJobId() != null ? job.getPangolinJobId() : ""
        ));
        return map;
    }

    // ── Cancel ──────────────────────────────────────────────────────────────

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        if (!IdValidator.isValidFlamencoId(jobId)) throw new ValidationException("Invalid job ID format");
        if (!userContextService.canAccessByFlamencoId(jobId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden", "jobId", jobId));
        }

        try {
            flamencoClient.setJobStatus(jobId,
                    new JobSetStatusRequest("cancel-requested", "Cancelled by user via Pangolin"));
            jobRepository.updateStatusByFlamencoJobId(jobId, "cancel-requested");
            log.info("Cancel requested for job {}", jobId);
            return ResponseEntity.ok(Map.of("message", "Cancel requested", "jobId", jobId));

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                log.warn("Cannot cancel job {} in its current state", jobId);
                return ResponseEntity.status(422)
                        .body(Map.of("error", "Job cannot be cancelled in its current state", "jobId", jobId));
            }
            throw e;
        }
    }

    // ── Job details & tasks ─────────────────────────────────────────────────

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobDetails(@PathVariable String jobId) {
        if (!IdValidator.isValidFlamencoId(jobId)) throw new ValidationException("Invalid job ID format");
        if (!userContextService.canAccessByFlamencoId(jobId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden", "jobId", jobId));
        }
        Map<String, Object> body = flamencoClient.getJob(jobId);
        log.info("Fetched job details for {}", jobId);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{jobId}/tasks")
    public ResponseEntity<Map<String, Object>> getJobTasks(@PathVariable String jobId) {
        if (!IdValidator.isValidFlamencoId(jobId)) throw new ValidationException("Invalid job ID");
        if (!userContextService.canAccessByFlamencoId(jobId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden", "jobId", jobId));
        }
        Map<String, Object> body = flamencoClient.getJobTasks(jobId);
        log.debug("Fetched tasks for job {}", jobId);
        return ResponseEntity.ok(body);
    }

    /**
     * Two-hop log fetch: first retrieves the log file path from Flamenco,
     * then fetches the log content. The second hop uses RestClient directly
     * because the path is dynamic and comes from Flamenco's response.
     *
     * The log path is validated before use to guard against SSRF.
     */
    @GetMapping("/{jobId}/tasks/{taskId}/log")
    public ResponseEntity<String> getTaskLog(
            @PathVariable String jobId,
            @PathVariable String taskId) {

        if (!IdValidator.isValidFlamencoId(jobId) || !IdValidator.isValidFlamencoId(taskId)) {
            return ResponseEntity.badRequest().body("Invalid ID format");
        }
        if (!userContextService.canAccessByFlamencoId(jobId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }

        try {
            TaskLogMeta logMeta = flamencoClient.getTaskLog(taskId);
            if (logMeta == null || logMeta.url() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No log available for this task");
            }

            String logPath = logMeta.url();
            if (!logPath.startsWith("/") || logPath.contains("..")) {
                log.error("Suspicious log path received from Flamenco: {}", logPath);
                return ResponseEntity.badRequest().body("Invalid log path");
            }

            String logContent = restClient.get().uri(logPath).retrieve().body(String.class);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body(logContent != null ? logContent : "Log file is empty");

        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Log not found");
        } catch (Exception e) {
            log.error("Error fetching log for task {}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch log: " + e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private int jobCount(Map<String, Object> body) {
        if (body != null && body.get("jobs") instanceof List<?> jobs) return jobs.size();
        return 0;
    }
}
