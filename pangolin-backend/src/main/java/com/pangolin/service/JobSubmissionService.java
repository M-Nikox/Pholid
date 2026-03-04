/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.service;

import com.github.luben.zstd.ZstdInputStream;
import com.pangolin.client.FlamencoClient;
import com.pangolin.config.PangolinProperties;
import com.pangolin.dto.JobSubmitRequest;
import com.pangolin.dto.JobTypesResponse;
import com.pangolin.exception.ValidationException;
import com.pangolin.job.Job;
import com.pangolin.job.JobRepository;
import com.pangolin.model.FrameValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Orchestrates render job submission: validates the blend file and frame range,
 * creates the job directory, and sends the job to Flamenco Manager.
 *
 * Previously all inline in RenderController (~200 lines). Now independently testable.
 */
@Service
public class JobSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(JobSubmissionService.class);

    private final FlamencoClient flamencoClient;
    private final FileStorageService storageService;
    private final PangolinProperties props;
    private final JobRepository jobRepository;

    public JobSubmissionService(FlamencoClient flamencoClient,
                                FileStorageService storageService,
                                PangolinProperties props,
                                JobRepository jobRepository) {
        this.flamencoClient = flamencoClient;
        this.storageService = storageService;
        this.props = props;
        this.jobRepository = jobRepository;
    }

    /**
     * Full submission pipeline. Throws ValidationException for bad input,
     * or propagates RestClientException for Flamenco connectivity failures
     * (handled by GlobalExceptionHandler).
     *
     * @param submittedBy the username to record as job owner (use {@code "anonymous"} when
     *                    auth is disabled)
     * @return the generated Pangolin job ID
     */
    public String submit(MultipartFile file,
                         String rawProjectName,
                         String frames,
                         String priority,
                         String computeMode,
                         String submittedBy) throws IOException {

        String safeName = sanitizeProjectName(rawProjectName);

        FrameValidationResult frameResult = validateFrames(frames);
        if (frameResult instanceof FrameValidationResult.Invalid invalid) {
            throw new ValidationException(invalid.error());
        }
        int totalFrames = ((FrameValidationResult.Valid) frameResult).totalFrames();

        validateBlendFile(file);

        int flamencoPriority = calculatePriority(priority);
        String jobId = createJobDirectory(file);
        String flamencoJobId = sendToManager(jobId, safeName, frames, totalFrames, flamencoPriority, computeMode);
        saveJobRecord(jobId, flamencoJobId, safeName, file.getOriginalFilename(), frames, submittedBy);
        return jobId;
    }

    // ============================
    // VALIDATION
    // ============================

    public FrameValidationResult validateFrames(String input) {
        int limit = props.frames().limit();
        int cap   = props.frames().cap();

        if (!input.matches("^\\d+(-\\d+)?$")) {
            return new FrameValidationResult.Invalid("Invalid frame format. Use 'Start-End' or a single number.");
        }

        try {
            if (input.contains("-")) {
                String[] parts = input.split("-");
                int start = Integer.parseInt(parts[0]);
                int end   = Integer.parseInt(parts[1]);

                if (start > end)  return new FrameValidationResult.Invalid("Start frame must be \u2264 end frame.");
                if (start < 1)    return new FrameValidationResult.Invalid("Frame numbers must be positive (minimum: 1)");
                if (start > cap || end > cap)
                                  return new FrameValidationResult.Invalid("Frame number too high. Maximum: " + cap);
                int total = end - start + 1;
                if (total > limit)
                    return new FrameValidationResult.Invalid("Too many frames requested. Maximum allowed: "
                            + limit + " (you requested: " + total + ")");

                return new FrameValidationResult.Valid(start, end, total);
            }

            int frame = Integer.parseInt(input);
            if (frame < 1)      return new FrameValidationResult.Invalid("Frame number must be positive (minimum: 1)");
            if (frame > cap)    return new FrameValidationResult.Invalid("Frame number too high. Maximum: " + cap);

            return new FrameValidationResult.Valid(frame, frame, 1);

        } catch (NumberFormatException e) {
            return new FrameValidationResult.Invalid("Invalid frame numbers.");
        }
    }

    private void validateBlendFile(MultipartFile file) {
        long maxBytes = props.file().maxSizeMb() * 1024L * 1024L;

        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".blend")) {
            throw new ValidationException("Only .blend files are allowed.");
        }
        if (file.getSize() > maxBytes) {
            throw new ValidationException("File too large. Maximum allowed size is "
                    + props.file().maxSizeMb() + " MB.");
        }

        try (InputStream raw = file.getInputStream();
             BufferedInputStream buffered = new BufferedInputStream(raw)) {

            buffered.mark(4);
            byte[] magic = buffered.readNBytes(4);
            buffered.reset();

            if (magic.length < 4) throw new ValidationException("File too small.");

            try (InputStream stream = selectDecompressionStream(buffered, magic)) {
                byte[] header = stream.readNBytes(7);
                if (header.length < 7) throw new ValidationException("File too small or corrupted.");
                if (!"BLENDER".equals(new String(header, StandardCharsets.US_ASCII)))
                    throw new ValidationException("File content is not a valid Blender .blend file.");
            }

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating blend file", e);
            throw new ValidationException("Error reading file for validation: " + e.getMessage());
        }
    }

    private String sanitizeProjectName(String name) {
        String safe = HtmlUtils.htmlEscape(name);
        int max = props.projectName().maxLength();
        return safe.length() > max ? safe.substring(0, max) : safe;
    }

    // ============================
    // JOB CREATION
    // ============================

    private String createJobDirectory(MultipartFile file) throws IOException {
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path jobDir = storageService.getJobRoot().resolve(jobId);
        Files.createDirectories(jobDir);
        file.transferTo(jobDir.resolve("input.blend"));
        return jobId;
    }

    private String sendToManager(String jobId,
                                  String projectName,
                                  String frames,
                                  int totalFrames,
                                  int priority,
                                  String computeMode) {

        Path jobDir  = storageService.getJobRoot().resolve(jobId);
        String jobType = resolveJobType(computeMode);
        boolean useGpu = jobType.startsWith("cycles-");
        String typeEtag = fetchJobTypeEtag(jobType);

        JobSubmitRequest request = new JobSubmitRequest(
                "Pangolin_" + projectName,
                jobType,
                priority,
                "web-client",
                typeEtag,
                Map.of(
                        "project",       projectName,
                        "submitter",     "Pangolin-Web",
                        "total_frames",  String.valueOf(totalFrames),
                        "frame_range",   frames,
                        "compute_mode",  computeMode,
                        "pangolin.job_id", jobId
                ),
                buildSettings(frames, jobDir.resolve("input.blend"), jobDir, useGpu)
        );

        Map<String, Object> flamencoJob = flamencoClient.submitJob(request);
        log.info("Job {} submitted with priority {} type={}", jobId, priority, jobType);
        if (flamencoJob != null && flamencoJob.get("id") instanceof String fid) {
            return fid;
        }
        return null;
    }

    private void saveJobRecord(String pangolinJobId, String flamencoJobId,
                                String projectName, String blendFileName,
                                String frames, String submittedBy) {
        try {
            Job job = new Job();
            job.setName("Pangolin_" + projectName);
            job.setStatus("active");
            job.setProjectName(projectName);
            job.setBlendFile(blendFileName);
            job.setFrames(frames);
            job.setSubmittedAt(OffsetDateTime.now());
            job.setFlamencoJobId(flamencoJobId);
            job.setSubmittedBy(submittedBy);
            job.setPangolinJobId(pangolinJobId);
            jobRepository.save(job);
        } catch (Exception e) {
            // Job tracking must never break the main submission flow
            log.warn("Failed to save job record for pangolin job {}: {}", pangolinJobId, e.getMessage());
        }
    }

    /**
     * Calls GET /api/v3/jobs/types to trigger loading of custom JS job types,
     * then returns the etag for the requested type. Flamenco requires this etag on submission.
     */
    private String fetchJobTypeEtag(String jobType) {
        try {
            JobTypesResponse response = flamencoClient.getJobTypes();
            if (response == null || response.jobTypes() == null) return null;

            return response.jobTypes().stream()
                    .filter(jt -> jobType.equals(jt.name()))
                    .map(JobTypesResponse.JobType::etag)
                    .findFirst()
                    .orElseGet(() -> {
                        log.warn("Job type '{}' not found in manager job types", jobType);
                        return null;
                    });

        } catch (Exception e) {
            log.warn("Could not fetch job types from manager: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildSettings(String frames, Path blendFile,
                                               Path jobDir, boolean useGpu) {
        Path outputDir = jobDir.resolve("output");
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("blendfile",             blendFile.toString());
        settings.put("frames",                frames);
        settings.put("render_output_path",    outputDir + "/######");
        settings.put("render_output_root",    outputDir.toString());
        settings.put("chunk_size",            3);
        settings.put("format",                "PNG");
        settings.put("fps",                   24);
        settings.put("has_previews",          false);
        settings.put("generate_preview_video",false);
        settings.put("image_file_extension",  ".png");
        settings.put("add_path_components",   0);
        return settings;
    }

    private String resolveJobType(String computeMode) {
        return switch (computeMode.toLowerCase()) {
            case "gpu-optix" -> "cycles-optix-gpu";
            case "gpu-cuda"  -> "cycles-cuda-gpu";
            default          -> "simple-blender-render";
        };
    }

    private int calculatePriority(String uiPriority) {
        return switch (uiPriority) {
            case "3" -> 80;
            case "2" -> 60;
            case "1" -> 40;
            default  -> 50;
        };
    }

    private InputStream selectDecompressionStream(InputStream in, byte[] magic) throws IOException {
        boolean zstd = magic[0] == (byte) 0x28 && magic[1] == (byte) 0xB5
                    && magic[2] == (byte) 0x2F && magic[3] == (byte) 0xFD;
        boolean gzip = magic[0] == (byte) 0x1F && magic[1] == (byte) 0x8B;
        if (zstd) return new ZstdInputStream(in);
        if (gzip) return new GZIPInputStream(in);
        return in;
    }
}