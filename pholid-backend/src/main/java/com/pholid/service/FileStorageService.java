/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.config.PholidProperties;
import com.pholid.exception.ValidationException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
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

    private final PholidProperties props;

    public FileStorageService(PholidProperties props) {
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

        Path tempZip = null;
        try {
            tempZip = Files.createTempFile("pholid-render-" + jobId + "-", ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip));
                 Stream<Path> files = Files.list(outputDir)) {
                Iterator<Path> iterator = files.filter(Files::isRegularFile)
                        .limit(props.download().maxFiles())
                        .iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    zipFile(path, zos);
                }
            }

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"render_" + jobId + ".zip\"");
            try (InputStream in = Files.newInputStream(tempZip)) {
                in.transferTo(response.getOutputStream());
            }
        } catch (IOException e) {
            log.error("Error creating zip for job {}", jobId, e);
            if (!response.isCommitted()) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Failed to create download archive (file read/write error).");
                return;
            }
            throw e;
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException deleteEx) {
                    log.warn("Failed to delete temp zip {}", tempZip, deleteEx);
                }
            }
        }
    }

    /**
     * Resolves the job directory for the given pholidJobId and validates it
     * stays within jobRoot to prevent path traversal attacks.
     */
    public Path resolveAndValidateJobDir(String pholidJobId) {
        Path jobRoot = getJobRoot().toAbsolutePath().normalize();
        Path jobDir = getJobRoot().resolve(pholidJobId).toAbsolutePath().normalize();

        if (!jobDir.startsWith(jobRoot)) {
            log.error("Path traversal attempt detected for pholidJobId: {}", pholidJobId);
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

    private void zipFile(Path file, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
