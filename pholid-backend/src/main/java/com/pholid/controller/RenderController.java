/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.controller;

import com.pholid.client.FlamencoClient;
import com.pholid.config.PholidProperties;
import com.pholid.exception.DeleteNotEnabledException;
import com.pholid.exception.JobConflictException;
import com.pholid.exception.ValidationException;
import com.pholid.model.SubmissionResult;
import com.pholid.service.FileStorageService;
import com.pholid.service.JobSubmissionService;
import com.pholid.validation.IdValidator;
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
import java.util.*;

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
    private final PholidProperties props;

    public RenderController(JobSubmissionService submissionService,
                            FileStorageService storageService,
                            FlamencoClient flamencoClient,
                            PholidProperties props) {
        this.submissionService = submissionService;
        this.storageService    = storageService;
        this.flamencoClient    = flamencoClient;
        this.props             = props;
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestParam("blendFile") MultipartFile file,
            @RequestParam("projectName") String projectName,
            @RequestParam("frames") String frames,
            @RequestParam(value = "priority", defaultValue = "1") String priority,
            @RequestParam(value = "computeMode", defaultValue = "gpu-cuda") String computeMode,
            @RequestParam(value = "blendFileName", required = false) String blendFileName)
            throws IOException {

        SubmissionResult result = submissionService.submit(
                file, projectName, frames, priority, computeMode, blendFileName);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Job submitted successfully!");
        body.put("jobId", result.jobId());
        if (result.hasWarnings()) {
            body.put("warnings", result.warnings());
        }

        return ResponseEntity.ok(body);
    }

    @GetMapping("/download/{jobId}")
    public void download(@PathVariable String jobId, HttpServletResponse response) throws IOException {
        if (!IdValidator.isValidPholidId(jobId)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid job ID format");
            return;
        }
        storageService.streamZip(jobId, response);
    }

    @GetMapping("/status/{jobId}")
    public Map<String, Object> status(@PathVariable String jobId) {
        if (!IdValidator.isValidPholidId(jobId)) {
            throw new ValidationException("Invalid job ID format");
        }
        return storageService.getStatus(jobId);
    }

    @DeleteMapping("/job/{flamencoJobId}")
    public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable String flamencoJobId) {
        if (!props.delete().enabled())                     throw new DeleteNotEnabledException();
        if (!IdValidator.isValidFlamencoId(flamencoJobId)) throw new ValidationException("Invalid Flamenco job ID format.");

        Map<String, Object> job = flamencoClient.getJob(flamencoJobId);
        if (job == null) throw new ValidationException("Job not found in Flamenco.");

        String status = (String) job.get("status");
        if (!Set.of("completed", "failed", "canceled").contains(status)) {
            throw new JobConflictException("Job cannot be deleted in status: " + status
                    + ". Only completed, failed, or canceled jobs may be deleted.");
        }

        String pholidJobId = extractPholidJobId(job, flamencoJobId);
        Path jobDir = storageService.resolveAndValidateJobDir(pholidJobId);

        flamencoClient.deleteJob(flamencoJobId);
        log.info("Job {} deleted from Flamenco", flamencoJobId);

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
                                "pholidJobId", pholidJobId
                        ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "message",       "Job deleted successfully.",
                "flamencoJobId", flamencoJobId,
                "pholidJobId", pholidJobId
        ));
    }

    @SuppressWarnings("unchecked")
    private String extractPholidJobId(Map<String, Object> job, String flamencoJobId) {
        try {
            Map<String, String> metadata = (Map<String, String>) job.get("metadata");
            String pholidJobId = metadata != null ? metadata.get("pholid.job_id") : null;

            if (!IdValidator.isValidPholidId(pholidJobId)) {
                log.warn("Job {} has missing or invalid pholid.job_id in metadata", flamencoJobId);
                throw new ValidationException("Job is missing a valid pholid.job_id in metadata. "
                        + "Cannot safely locate output files for deletion.");
            }
            return pholidJobId;
        } catch (ClassCastException e) {
            log.warn("Could not read metadata for job {}", flamencoJobId, e);
            throw new ValidationException("Could not read job metadata.");
        }
    }
}
