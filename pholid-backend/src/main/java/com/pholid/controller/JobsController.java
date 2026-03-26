/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.controller;

import com.pholid.client.FlamencoClient;
import com.pholid.dto.JobSetStatusRequest;
import com.pholid.dto.TaskLogMeta;
import com.pholid.exception.ValidationException;
import com.pholid.validation.IdValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Job query, cancel, and task log endpoints.
 * Uses FlamencoClient for all Flamenco calls.
 * RestClient is injected directly only for the two-hop log content fetch,
 * which cannot be modelled as a static @HttpExchange path.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobsController {

    private static final Logger log = LoggerFactory.getLogger(JobsController.class);

    private static final String ACTIVE_STATUSES =
            "active,queued,paused,pause-requested,cancel-requested,under-construction,requeueing";

    private final FlamencoClient flamencoClient;
    private final RestClient restClient;

    public JobsController(FlamencoClient flamencoClient, RestClient restClient) {
        this.flamencoClient = flamencoClient;
        this.restClient     = restClient;
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveJobs() {
        Map<String, Object> body = flamencoClient.getJobs(ACTIVE_STATUSES, null, null);
        log.info("Fetched active jobs. Count: {}", jobCount(body));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/previous")
    public ResponseEntity<Map<String, Object>> getPreviousJobs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        if (limit < 1 || limit > 200) throw new ValidationException("Limit must be between 1 and 200");
        if (offset < 0)               throw new ValidationException("Offset must be >= 0");

        Map<String, Object> body = flamencoClient.getJobs("completed,failed,canceled", limit, offset);
        log.info("Fetched previous jobs. Count: {}, Offset: {}, Limit: {}", jobCount(body), offset, limit);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        if (!IdValidator.isValidFlamencoId(jobId)) throw new ValidationException("Invalid job ID format");

        try {
            flamencoClient.setJobStatus(jobId,
                    new JobSetStatusRequest("cancel-requested", "Cancelled by user via Pholid"));
            log.info("Cancel requested for job {}", jobId);
            return ResponseEntity.ok(Map.of("message", "Cancel requested", "jobId", jobId));

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) { // 422 = UNPROCESSABLE_ENTITY
                log.warn("Cannot cancel job {} in its current state", jobId);
                return ResponseEntity.status(422)   // 422 = UNPROCESSABLE_ENTITY
                .body(Map.of("error", "Job cannot be cancelled in its current state", "jobId", jobId)); 
            }
            throw e;
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobDetails(@PathVariable String jobId) {
        if (!IdValidator.isValidFlamencoId(jobId)) throw new ValidationException("Invalid job ID format");
        Map<String, Object> body = flamencoClient.getJob(jobId);
        log.info("Fetched job details for {}", jobId);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{jobId}/tasks")
    public ResponseEntity<Map<String, Object>> getJobTasks(@PathVariable String jobId) {
        if (!IdValidator.isValidFlamencoId(jobId)) throw new ValidationException("Invalid job ID");
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

        try {
            TaskLogMeta logMeta = flamencoClient.getTaskLog(taskId);
            if (logMeta == null || logMeta.url() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No log available for this task");
            }

            // SSRF guard: log path must be a relative server path, not an external URL
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
                    .body("Failed to fetch log");
        }
    }

    private int jobCount(Map<String, Object> body) {
        if (body != null && body.get("jobs") instanceof List<?> jobs) return jobs.size();
        return 0;
    }
}