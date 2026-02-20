/*
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Controller for managing job queries to Flamenco Manager.
 * Provides endpoints for listing active and completed jobs.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobsController {

    private static final Logger log = LoggerFactory.getLogger(JobsController.class);

    private final RestTemplate restTemplate;
    private final String managerUrl;

    public JobsController(RestTemplate restTemplate,
                         @Value("${flamenco.manager.url:http://flamenco-manager:8080}") String managerUrl) {
        this.restTemplate = restTemplate;
        this.managerUrl = managerUrl;
    }

    /**
     * Get all active jobs for the "Active Sessions" panel.
     * Returns jobs with statuses: active, queued, paused, pause-requested, cancel-requested
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveJobs() {
        String url = managerUrl + "/api/v3/jobs?status_in=active,queued,paused,pause-requested,cancel-requested,under-construction";
        
        log.debug("Fetching active jobs from: {}", url);
        
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = 
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.getForEntity(url, Map.class);
            
            Map<String, Object> body = response.getBody();
            log.info("Successfully fetched active jobs. Count: {}", 
                body != null && body.containsKey("jobs") ? 
                    ((java.util.List<?>) body.get("jobs")).size() : 0);
            
            return ResponseEntity.ok(body);
            
        } catch (Exception e) {
            log.error("Error fetching active jobs from Flamenco Manager", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to fetch active jobs",
                        "message", e.getMessage(),
                        "jobs", java.util.List.of()
                    ));
        }
    }

    /**
     * Get previous (completed/failed/canceled) jobs for the "Previous Sessions" panel.
     * Supports pagination via limit and offset parameters.
     * 
     * @param limit Maximum number of jobs to return (default: 50)
     * @param offset Number of jobs to skip for pagination (default: 0)
     */
    @GetMapping("/previous")
    public ResponseEntity<Map<String, Object>> getPreviousJobs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        // Validate parameters
        if (limit < 1 || limit > 200) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error", "Invalid limit parameter",
                        "message", "Limit must be between 1 and 200"
                    ));
        }
        
        if (offset < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error", "Invalid offset parameter",
                        "message", "Offset must be >= 0"
                    ));
        }
        
        String url = String.format(
            "%s/api/v3/jobs?status_in=completed,failed,canceled&limit=%d&offset=%d",
            managerUrl, limit, offset
        );
        // Note: status_in filter ensures only completed/failed/canceled jobs are
        // fetched from Flamenco, avoiding full history scans as job count grows.
        
        log.debug("Fetching previous jobs from: {}", url);
        
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = 
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.getForEntity(url, Map.class);
            
            Map<String, Object> body = response.getBody();
            log.info("Successfully fetched previous jobs. Count: {}, Offset: {}, Limit: {}", 
                body != null && body.containsKey("jobs") ? 
                    ((java.util.List<?>) body.get("jobs")).size() : 0,
                offset, limit);
            
            return ResponseEntity.ok(body);
            
        } catch (Exception e) {
            log.error("Error fetching previous jobs from Flamenco Manager", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to fetch previous jobs",
                        "message", e.getMessage(),
                        "jobs", java.util.List.of()
                    ));
        }
    }

    /**
     * Cancel a job by requesting status change to cancel-requested.
     *
     * @param jobId The Flamenco job ID (UUID format)
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {

        if (jobId == null || jobId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Job ID cannot be empty"));
        }

        if (!jobId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid job ID format"));
        }

        String url = managerUrl + "/api/v3/jobs/" + jobId + "/setstatus";

        log.debug("Requesting cancel for job {} at: {}", jobId, url);

        try {
            restTemplate.postForObject(url, Map.of(
                "status", "cancel-requested",
                "reason", "Cancelled by user via Pangolin"
            ), String.class);

            log.info("Cancel requested for job {}", jobId);
            return ResponseEntity.ok(Map.of(
                "message", "Cancel requested",
                "jobId", jobId
            ));

        } catch (org.springframework.web.client.HttpClientErrorException.UnprocessableEntity e) {
            log.warn("Cannot cancel job {} in its current state", jobId);
            return ResponseEntity.status(422)
                    .body(Map.of(
                        "error", "Job cannot be cancelled in its current state",
                        "jobId", jobId
                    ));

        } catch (Exception e) {
            log.error("Error cancelling job {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to cancel job",
                        "jobId", jobId,
                        "message", e.getMessage()
                    ));
        }
    }

    /**
     * Get details for a specific job by ID.
     * Useful for refreshing single job status without fetching all jobs.
     * 
     * @param jobId The Flamenco job ID (UUID format)
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobDetails(@PathVariable String jobId) {
        
        // Basic validation
        if (jobId == null || jobId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Job ID cannot be empty"));
        }

        if (!jobId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid job ID format"));
        }
        
        String url = managerUrl + "/api/v3/jobs/" + jobId;
        
        log.debug("Fetching job details for {} from: {}", jobId, url);
        
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = 
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.getForEntity(url, Map.class);
            
            log.info("Successfully fetched job details for {}", jobId);
            return ResponseEntity.ok(response.getBody());
            
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.warn("Job not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "error", "Job not found",
                        "jobId", jobId
                    ));
            
        } catch (Exception e) {
            log.error("Error fetching job details for {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to fetch job details",
                        "jobId", jobId,
                        "message", e.getMessage()
                    ));
        }
    }
    /**
     * Get all tasks for a specific job.
     */
    @GetMapping("/{jobId}/tasks")
    public ResponseEntity<?> getJobTasks(@PathVariable String jobId) {
        if (jobId == null || jobId.isBlank() ||
            !jobId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid job ID"));
        }
        String url = managerUrl + "/api/v3/jobs/" + jobId + "/tasks";
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.getForEntity(url, Map.class);
            log.debug("Fetched tasks for job {}", jobId);
            return ResponseEntity.ok(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        } catch (Exception e) {
            log.error("Error fetching tasks for job {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch tasks"));
        }
    }

    /**
     * Get log text for a specific task.
     * Two-hop: first asks Flamenco for the log file URL, then fetches the file content.
     */
    @GetMapping("/{jobId}/tasks/{taskId}/log")
    public ResponseEntity<String> getTaskLog(
            @PathVariable String jobId,
            @PathVariable String taskId) {
        String uuidPattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        if (jobId == null || !jobId.matches(uuidPattern) ||
            taskId == null || !taskId.matches(uuidPattern)) {
            return ResponseEntity.badRequest().body("Invalid ID format");
        }
        try {
            // Step 1: get the log file URL from Flamenco
            String logMetaUrl = managerUrl + "/api/v3/tasks/" + taskId + "/log";
            @SuppressWarnings("unchecked")
            Map<String, Object> logMeta = restTemplate.getForObject(logMetaUrl, Map.class);
            if (logMeta == null || !logMeta.containsKey("url")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No log available for this task");
            }
            // Step 2: fetch the actual log text from Flamenco Manager
            String logFileUrl = managerUrl + logMeta.get("url").toString();
            String logText = restTemplate.getForObject(logFileUrl, String.class);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body(logText != null ? logText : "Log file is empty");
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Log not found");
        } catch (Exception e) {
            log.error("Error fetching log for task {}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch log: " + e.getMessage());
        }
    }

}