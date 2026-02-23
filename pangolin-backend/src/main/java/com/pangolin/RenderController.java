/*
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin;

import com.github.luben.zstd.ZstdInputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/")
public class RenderController {

    private static final Logger log = LoggerFactory.getLogger(RenderController.class);

    @Value("${storage.root:/shared}")
    private String storageRoot;

    private String getJobRoot() {
        return storageRoot.endsWith("/") ? storageRoot + "jobs/" : storageRoot + "/jobs/";
    }
    private static final int FRAME_LIMIT = 3500;
    private static final int FRAME_CAP = 100000;
    private static final int MAX_PROJECT_NAME_LENGTH = 100;
    private static final long MAX_FILE_SIZE = 512 * 1024 * 1024; // 512 MB
    private static final int MAX_DOWNLOAD_FILES = 5000;

    private final RestTemplate restTemplate;
    private final String managerUrl;
    private final boolean deleteEnabled;

    public RenderController(RestTemplate restTemplate,
                            @Value("${flamenco.manager.url:http://flamenco-manager:8080}") String managerUrl,
                            @Value("${pangolin.delete.enabled:false}") boolean deleteEnabled) {
        this.restTemplate = restTemplate;
        this.managerUrl = managerUrl;
        this.deleteEnabled = deleteEnabled;
        log.info("Delete feature is {}", deleteEnabled ? "ENABLED" : "DISABLED");
    }

    // ============================
    // ROUTES
    // ============================

    @PostMapping("submit")
    public ResponseEntity<Map<String, String>> submit(@RequestParam("blendFile") MultipartFile file,
                                                      @RequestParam("projectName") String projectName,
                                                      @RequestParam("frames") String frames,
                                                      @RequestParam(value = "priority", defaultValue = "1") String priority,
                                                      @RequestParam(value = "computeMode", defaultValue = "gpu-cuda") String computeMode) {

        String safeName = HtmlUtils.htmlEscape(projectName);
        if (safeName.length() > MAX_PROJECT_NAME_LENGTH) {
            safeName = safeName.substring(0, MAX_PROJECT_NAME_LENGTH);
        }

        FrameRange range = validateFrames(frames);
        if (!range.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", range.error()));
        }

        String fileError = validateBlendFile(file);
        if (fileError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", fileError));
        }

        int flamencoPriority = calculatePriority(priority);

        try {
            String jobId = createJobDirectory(file);
            sendToManager(jobId, safeName, frames, range.totalFrames(), flamencoPriority, computeMode);
            return ResponseEntity.ok(Map.of(
                    "message", "Job submitted successfully!",
                    "jobId", jobId
            ));
        } catch (Exception e) {
            log.error("Error submitting job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error sending to Manager: " + e.getMessage()));
        }
    }

    @GetMapping("download/{jobId}")
    public void download(@PathVariable String jobId,
                         HttpServletResponse response) throws IOException {

        if (!isValidJobId(jobId)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid job ID format");
            return;
        }

        Path outputDir = resolveOutputPath(jobId);

        if (!Files.exists(outputDir)) {
            response.sendError(HttpStatus.NOT_FOUND.value(),
                    "Render folder not found. Job might still be rendering or failed.");
            return;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"render_" + jobId + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());
            Stream<Path> files = Files.list(outputDir)) {

            files.filter(Files::isRegularFile)
                .limit(MAX_DOWNLOAD_FILES)
                .forEach(path -> zipFile(path, zos));

        } catch (IOException e) {
            log.error("Error creating zip for job {}", jobId, e);
        }
    }

    @GetMapping("status/{jobId}")
    public Map<String, Object> status(@PathVariable String jobId) {

        if (!isValidJobId(jobId)) {
            return Map.of("error", "Invalid job ID format");
        }

        Path outputDir = resolveOutputPath(jobId);
        Map<String, Object> result = new LinkedHashMap<>();

        boolean ready = Files.exists(outputDir);
        result.put("ready", ready);

        if (ready) {
            try (Stream<Path> stream = Files.list(outputDir)) {
                long count = stream
                        .filter(p -> p.toString().toLowerCase().endsWith(".png"))
                        .count();
                result.put("fileCount", count);
            } catch (IOException e) {
                log.warn("Could not list files for job {}", jobId, e);
                result.put("fileCount", 0);
            }
        } else {
            result.put("fileCount", 0);
        }

        return result;
    }

    // ============================
    // DELETE JOB
    // ============================

    @DeleteMapping("api/render/job/{flamencoJobId}")
    public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable String flamencoJobId) {

        // Change 3: Gate on ENABLE_DELETE flag
        if (!deleteEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Delete feature is not enabled on this instance."));
        }

        // Change 5: Validate Flamenco UUID format
        if (!isValidFlamencoJobId(flamencoJobId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid Flamenco job ID format."));
        }

        // Change 6: Fetch job from Flamenco and verify status is terminal
        Map<String, Object> job;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fetched = restTemplate.getForObject(
                managerUrl + "/api/v3/jobs/" + flamencoJobId, Map.class);
            job = fetched;
        } catch (Exception e) {
            log.error("Failed to fetch job {} from Flamenco before delete", flamencoJobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not verify job status before deletion."));
        }

        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Job not found in Flamenco."));
        }

        String status = (String) job.get("status");
        Set<String> terminalStatuses = Set.of("completed", "failed", "canceled");
        if (!terminalStatuses.contains(status)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Job cannot be deleted in status: " + status
                            + ". Only completed, failed, or canceled jobs may be deleted."));
        }

        // Change 7: Extract and validate pangolin.job_id from metadata
        String pangolinJobId = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> metadata = (Map<String, String>) job.get("metadata");
            if (metadata != null) {
                pangolinJobId = metadata.get("pangolin.job_id");
            }
        } catch (Exception e) {
            log.warn("Could not read metadata for job {}", flamencoJobId, e);
        }

        if (pangolinJobId == null || !pangolinJobId.matches("[a-f0-9]{16}")) {
            log.warn("Job {} has missing or invalid pangolin.job_id in metadata", flamencoJobId);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(Map.of("error", "Job is missing a valid pangolin.job_id in metadata. "
                            + "Cannot safely locate output files for deletion."));
        }

        // Change 8: Path traversal check
        Path jobRoot = Path.of(getJobRoot()).toAbsolutePath().normalize();
        Path jobDir;
        try {
            jobDir = Path.of(getJobRoot(), pangolinJobId).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.error("Failed to resolve job directory path for {}", pangolinJobId, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid job directory path."));
        }

        if (!jobDir.startsWith(jobRoot)) {
            log.error("Path traversal attempt detected for pangolinJobId: {}", pangolinJobId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid job ID — path resolution failed."));
        }

        // Change 9: DELETE from Flamenco first
        try {
            restTemplate.delete(managerUrl + "/api/v3/jobs/" + flamencoJobId);
            log.info("Job {} deleted from Flamenco", flamencoJobId);
        } catch (Exception e) {
            log.error("Failed to delete job {} from Flamenco", flamencoJobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete job from Flamenco. No files were touched."));
        }

        // Change 10: Delete filesystem directory second
        if (Files.exists(jobDir)) {
            try {
                deleteDirectory(jobDir);
                log.info("Job directory deleted: {}", jobDir);
            } catch (IOException e) {
                log.error("Failed to delete job directory {} after Flamenco deletion", jobDir, e);
                return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                        .body(Map.of(
                            "warning", "Job was removed from Flamenco but output files could not be deleted.",
                            "path", jobDir.toString(),
                            "flamencoJobId", flamencoJobId,
                            "pangolinJobId", pangolinJobId
                        ));
            }
        } else {
            log.info("Job directory {} does not exist, skipping filesystem deletion", jobDir);
        }

        return ResponseEntity.ok(Map.of(
            "message", "Job deleted successfully.",
            "flamencoJobId", flamencoJobId,
            "pangolinJobId", pangolinJobId
        ));
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            List<String> failed = new ArrayList<>();
            for (Path path : paths) {
                if (!path.toFile().delete() && Files.exists(path)) {
                    failed.add(path.toString());
                }
            }
            if (!failed.isEmpty()) {
                throw new IOException("Could not delete " + failed.size()
                        + " file(s): " + String.join(", ", failed));
            }
        }
    }

    private boolean isValidFlamencoJobId(String id) {
        return id != null && id.matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
    }

    // ============================
    // PRIORITY
    // ============================

    private int calculatePriority(String uiPriority) {
        return switch (uiPriority) {
            case "3" -> 80;
            case "2" -> 60;
            case "1" -> 40;
            default -> 50;
        };
    }

    // ============================
    // FRAME VALIDATION
    // ============================

    private FrameRange validateFrames(String input) {

        if (!input.matches("^\\d+(-\\d+)?$")) {
            return FrameRange.invalid("Invalid frame format. Use 'Start-End' or a single number.");
        }

        try {
            if (input.contains("-")) {
                String[] parts = input.split("-");
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);

                if (start > end)
                    return FrameRange.invalid("Start frame must be ≤ end frame.");
                if (start < 1)
                    return FrameRange.invalid("Frame numbers must be positive (minimum: 1)");
                if (start > FRAME_CAP || end > FRAME_CAP)
                    return FrameRange.invalid("Frame number too high. Maximum: " + FRAME_CAP);

                int total = end - start + 1;
                if (total > FRAME_LIMIT)
                    return FrameRange.invalid("Too many frames requested. Maximum allowed: "
                            + FRAME_LIMIT + " (You requested: " + total + ")");

                return FrameRange.valid(start, end, total);
            }

            int frame = Integer.parseInt(input);

            if (frame < 1)
                return FrameRange.invalid("Frame number must be positive (minimum: 1)");
            if (frame > FRAME_CAP)
                return FrameRange.invalid("Frame number too high. Maximum: " + FRAME_CAP);

            return FrameRange.valid(frame, frame, 1);

        } catch (NumberFormatException e) {
            return FrameRange.invalid("Invalid frame numbers.");
        }
    }

    // ============================
    // FILE VALIDATION
    // ============================

    private String validateBlendFile(MultipartFile file) {

        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".blend")) {
            return "Only .blend files are allowed.";
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return "File too large. Maximum allowed size is "
                    + (MAX_FILE_SIZE / (1024 * 1024)) + " MB.";
        }

        try (InputStream raw = file.getInputStream();
             BufferedInputStream buffered = new BufferedInputStream(raw)) {

            buffered.mark(4);
            byte[] magic = buffered.readNBytes(4);
            buffered.reset();

            if (magic.length < 4)
                return "File too small.";

            try (InputStream stream = selectStream(buffered, magic)) {
                byte[] header = stream.readNBytes(7);
                if (header.length < 7)
                    return "File too small or corrupted.";

                if (!"BLENDER".equals(new String(header, StandardCharsets.US_ASCII)))
                    return "File content is not a valid Blender .blend file.";
            }

        } catch (Exception e) {
            log.error("Error validating blend file", e);
            return "Error reading file for validation: " + e.getMessage();
        }

        return null;
    }

    private InputStream selectStream(InputStream in, byte[] magic) throws IOException {

        boolean zstd = magic[0] == (byte) 0x28 &&
                magic[1] == (byte) 0xB5 &&
                magic[2] == (byte) 0x2F &&
                magic[3] == (byte) 0xFD;

        boolean gzip = magic[0] == (byte) 0x1F &&
                magic[1] == (byte) 0x8B;

        if (zstd) return new ZstdInputStream(in);
        if (gzip) return new GZIPInputStream(in);

        return in;
    }

    // ============================
    // JOB CREATION
    // ============================

    private String createJobDirectory(MultipartFile file) throws IOException {

        String jobId = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        Path jobDir = Path.of(getJobRoot(), jobId);
        Files.createDirectories(jobDir);

        file.transferTo(jobDir.resolve("input.blend"));

        return jobId;
    }

    private void sendToManager(String jobId,
                               String projectName,
                               String frames,
                               int totalFrames,
                               int priority,
                               String computeMode) {

        Path jobDir = Path.of(getJobRoot(), jobId);
        Path blendFile = jobDir.resolve("input.blend");

        String jobType;
        switch (computeMode.toLowerCase()) {
            case "gpu-optix" -> jobType = "cycles-optix-gpu";
            case "gpu-cuda"  -> jobType = "cycles-cuda-gpu";
            default          -> jobType = "simple-blender-render";
        }
        boolean useGpu = jobType.startsWith("cycles-");

        // Fetch job types from manager to get the type_etag — this is what
        // the Blender add-on does and Flamenco expects it on submission.
        String typeEtag = fetchJobTypeEtag(jobType);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Pangolin_" + projectName);
        payload.put("type", jobType);
        payload.put("priority", priority);
        payload.put("submitter_platform", "web-client");
        if (typeEtag != null) {
            payload.put("type_etag", typeEtag);
        }

        payload.put("metadata", Map.of(
                "project", projectName,
                "submitter", "Pangolin-Web",
                "total_frames", String.valueOf(totalFrames),
                "frame_range", frames,
                "compute_mode", computeMode,
                "pangolin.job_id", jobId
        ));

        payload.put("settings", buildSettings(frames, blendFile, jobDir, useGpu));

        String url = managerUrl + "/api/v3/jobs";
        try {
            restTemplate.postForObject(url, payload, String.class);
            log.info("Job {} submitted to manager at {} with priority {} type={}", jobId, url, priority, jobType);
        } catch (Exception e) {
            log.error("Failed to send job {} to manager", jobId, e);
            throw e;
        }
    }

    /**
     * Calls GET /api/v3/jobs/types to load all job types (including custom scripts)
     * and returns the etag for the requested type. Flamenco only loads custom JS
     * job types when this endpoint is called, so we must call it before submitting.
     */
    @SuppressWarnings("unchecked")
    private String fetchJobTypeEtag(String jobType) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    managerUrl + "/api/v3/jobs/types", Map.class);
            if (response == null) return null;

            List<Map<String, Object>> jobTypes = (List<Map<String, Object>>) response.get("job_types");
            if (jobTypes == null) return null;

            for (Map<String, Object> jt : jobTypes) {
                if (jobType.equals(jt.get("name"))) {
                    String etag = (String) jt.get("etag");
                    log.info("Found job type '{}' with etag={}", jobType, etag);
                    return etag;
                }
            }
            log.warn("Job type '{}' not found in manager job types. Available: {}",
                    jobType, jobTypes.stream().map(jt -> jt.get("name")).toList());
        } catch (Exception e) {
            log.warn("Could not fetch job types from manager: {}", e.getMessage());
        }
        return null;
    }

    private @NonNull Map<String, Object> buildSettings(String frames,
                                                       Path blendFile,
                                                       Path jobDir,
                                                       boolean useGpu) {

        Path outputDir = jobDir.resolve("output");
        String outputPattern = outputDir.toString() + "/######";

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("blendfile", blendFile.toString());
        settings.put("frames", frames);
        settings.put("render_output_path", outputPattern);
        settings.put("render_output_root", outputDir.toString());
        settings.put("chunk_size", 3);
        settings.put("format", "PNG");
        settings.put("fps", 24);
        settings.put("has_previews", false);
        settings.put("generate_preview_video", false);
        settings.put("image_file_extension", ".png");

        // Both job types declare add_path_components as required
        settings.put("add_path_components", 0);

        return settings;
    }

    private Path resolveOutputPath(String jobId) {
        return Path.of(getJobRoot(), jobId, "output");
    }

    private boolean isValidJobId(String jobId) {
        return jobId != null && jobId.matches("[a-f0-9]{16}");
    }

    private void zipFile(Path file, ZipOutputStream zos) {
        try {
            zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
            Files.copy(file, zos);
            zos.closeEntry();
        } catch (IOException e) {
            log.warn("Failed to add {} to zip: {}", file, e.getMessage());
        }
    }

    // ============================
    // VALUE OBJECT
    // ============================

    private record FrameRange(boolean valid,
                              int start,
                              int end,
                              int totalFrames,
                              String error) {

        static FrameRange valid(int s, int e, int total) {
            return new FrameRange(true, s, e, total, null);
        }

        static FrameRange invalid(String msg) {
            return new FrameRange(false, 0, 0, 0, msg);
        }

        public boolean valid() { return valid; }
        public String error() { return error; }
        public int totalFrames() { return totalFrames; }
    }
}
