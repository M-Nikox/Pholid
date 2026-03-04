/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.controller;

import com.pangolin.audit.AuditLogService;
import com.pangolin.client.FlamencoClient;
import com.pangolin.config.PangolinProperties;
import com.pangolin.exception.DeleteNotEnabledException;
import com.pangolin.exception.JobConflictException;
import com.pangolin.exception.ValidationException;
import com.pangolin.service.FileStorageService;
import com.pangolin.service.JobSubmissionService;
import com.pangolin.validation.IdValidator;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Render job endpoints.
 */
@RestController
@RequestMapping("/api/render")
public class RenderController {

    private static final Logger log = LoggerFactory.getLogger(RenderController.class);

    private final JobSubmissionService submissionService;
    private final FileStorageService storageService;
    private final FlamencoClient flamencoClient;
    private final PangolinProperties props;
    private final AuditLogService auditLogService;

    public RenderController(JobSubmissionService submissionService,
                            FileStorageService storageService,
                            FlamencoClient flamencoClient,
                            PangolinProperties props,
                            AuditLogService auditLogService) {
        this.submissionService = submissionService;
        this.storageService    = storageService;
        this.flamencoClient    = flamencoClient;
        this.props             = props;
        this.auditLogService   = auditLogService;
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, String>> submit(
            @RequestParam("blendFile") MultipartFile file,
            @RequestParam("projectName") String projectName,
            @RequestParam("frames") String frames,
            @RequestParam(value = "priority", defaultValue = "1") String priority,
            @RequestParam(value = "computeMode", defaultValue = "gpu-cuda") String computeMode)
            throws IOException {

        String jobId = submissionService.submit(file, projectName, frames, priority, computeMode);
        auditLogService.logAction("JOB_SUBMITTED", "JOB", jobId, "project=" + projectName);
        return ResponseEntity.ok(Map.of(
                "message", "Job submitted successfully!",
                "jobId", jobId
        ));
    }

    @GetMapping("/download/{jobId}")
    public void download(@PathVariable String jobId, HttpServletResponse response) throws IOException {
        if (!IdValidator.isValidPangolinId(jobId)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid job ID format");
            return;
        }
        storageService.streamZip(jobId, response);
    }

    @GetMapping("/status/{jobId}")
    public Map<String, Object> status(@PathVariable String jobId) {
        if (!IdValidator.isValidPangolinId(jobId)) {
            throw new ValidationException("Invalid job ID format");
        }
        return storageService.getStatus(jobId);
    }

    @DeleteMapping("/job/{flamencoJobId}")
    public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable String flamencoJobId) {
        if (!props.delete().enabled())              throw new DeleteNotEnabledException();
        if (!IdValidator.isValidFlamencoId(flamencoJobId)) throw new ValidationException("Invalid Flamenco job ID format.");

        Map<String, Object> job = flamencoClient.getJob(flamencoJobId);
        if (job == null) throw new ValidationException("Job not found in Flamenco.");

        String status = (String) job.get("status");
        if (!Set.of("completed", "failed", "canceled").contains(status)) {
            throw new JobConflictException("Job cannot be deleted in status: " + status
                    + ". Only completed, failed, or canceled jobs may be deleted.");
        }

        String pangolinJobId = extractPangolinJobId(job, flamencoJobId);
        Path jobDir = storageService.resolveAndValidateJobDir(pangolinJobId);

        flamencoClient.deleteJob(flamencoJobId);
        log.info("Job {} deleted from Flamenco", flamencoJobId);
        auditLogService.logAction("JOB_DELETED", "JOB", pangolinJobId,
                "flamencoJobId=" + flamencoJobId);

        if (Files.exists(jobDir)) {
            try {
                storageService.deleteDirectory(jobDir);
                log.info("Job directory deleted: {}", jobDir);
            } catch (IOException e) {
                log.error("Failed to delete job directory {} after Flamenco deletion", jobDir, e);
                return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                        .body(Map.of(
                                "warning",       "Job removed from Flamenco but output files could not be deleted.",
                                "path",          jobDir.toString(),
                                "flamencoJobId", flamencoJobId,
                                "pangolinJobId", pangolinJobId
                        ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "message",       "Job deleted successfully.",
                "flamencoJobId", flamencoJobId,
                "pangolinJobId", pangolinJobId
        ));
    }

    @SuppressWarnings("unchecked")
    private String extractPangolinJobId(Map<String, Object> job, String flamencoJobId) {
        try {
            Map<String, String> metadata = (Map<String, String>) job.get("metadata");
            String pangolinJobId = metadata != null ? metadata.get("pangolin.job_id") : null;

            if (!IdValidator.isValidPangolinId(pangolinJobId)) {
                log.warn("Job {} has missing or invalid pangolin.job_id in metadata", flamencoJobId);
                throw new ValidationException("Job is missing a valid pangolin.job_id in metadata. "
                        + "Cannot safely locate output files for deletion.");
            }
            return pangolinJobId;
        } catch (ClassCastException e) {
            log.warn("Could not read metadata for job {}", flamencoJobId, e);
            throw new ValidationException("Could not read job metadata.");
        }
    }
}