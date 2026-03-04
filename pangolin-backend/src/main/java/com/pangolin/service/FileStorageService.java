/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.service;

import com.pangolin.config.PangolinProperties;
import com.pangolin.exception.ValidationException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Handles all filesystem operations: job directory lifecycle, output status checks,
 * zip streaming for download, and safe deletion with path traversal protection.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final PangolinProperties props;

    public FileStorageService(PangolinProperties props) {
        this.props = props;
    }

    public Path getJobRoot() {
        String root = props.storage().root();
        String normalised = root.endsWith("/") ? root + "jobs/" : root + "/jobs/";
        return Path.of(normalised);
    }

    public Path resolveOutputPath(String jobId) {
        return getJobRoot().resolve(jobId).resolve("output");
    }

    public Map<String, Object> getStatus(String jobId) {
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
                result.put("fileCount", 0L);
            }
        } else {
            result.put("fileCount", 0L);
        }
        return result;
    }

    public void streamZip(String jobId, HttpServletResponse response) throws IOException {
        Path outputDir = resolveOutputPath(jobId);

        if (!Files.exists(outputDir)) {
            response.sendError(HttpStatus.NOT_FOUND.value(),
                    "Render folder not found. Job might still be rendering or failed.");
            return;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"render_" + jobId + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());
             Stream<Path> files = Files.list(outputDir)) {

            files.filter(Files::isRegularFile)
                    .limit(props.download().maxFiles())
                    .forEach(path -> zipFile(path, zos));

        } catch (IOException e) {
            log.error("Error creating zip for job {}", jobId, e);
        }
    }

    /**
     * Resolves the job directory for the given pangolinJobId and validates it
     * stays within jobRoot to prevent path traversal attacks.
     */
    public Path resolveAndValidateJobDir(String pangolinJobId) {
        Path jobRoot = getJobRoot().toAbsolutePath().normalize();
        Path jobDir = getJobRoot().resolve(pangolinJobId).toAbsolutePath().normalize();

        if (!jobDir.startsWith(jobRoot)) {
            log.error("Path traversal attempt detected for pangolinJobId: {}", pangolinJobId);
            throw new ValidationException("Invalid job ID, path resolution failed.");
        }
        return jobDir;
    }

    public void deleteDirectory(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
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

    private void zipFile(Path file, ZipOutputStream zos) {
        try {
            zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
            Files.copy(file, zos);
            zos.closeEntry();
        } catch (IOException e) {
            log.warn("Failed to add {} to zip: {}", file, e.getMessage());
        }
    }
}