/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.config.PholidProperties;
import com.pholid.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles zip project extraction, zip bomb protection, blend file search,
 * and absolute path scanning for linked asset warnings.
 *
 * Called by JobSubmissionService when the uploaded file is a .zip.
 */
@Service
public class ZipSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ZipSubmissionService.class);

    // PK magic bytes that identify a valid zip file
    private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };

    // Absolute path patterns that suggest artist-local paths which will not resolve on workers
    private static final List<String> ABSOLUTE_PATH_PATTERNS = List.of(
            "/home/", "/Users/", "/root/", "/opt/",
            "C:\\", "D:\\", "E:\\", "F:\\",
            "C:/", "D:/", "E:/", "F:/"
    );

    private static final int BUFFER_SIZE = 8192;

    private final PholidProperties props;
    private final FileStorageService storageService;

    public ZipSubmissionService(PholidProperties props, FileStorageService storageService) {
        this.props = props;
        this.storageService = storageService;
    }

    // ============================
    // PUBLIC API
    // ============================

    /**
     * Validates zip magic bytes and checks the file is not larger than the upload limit.
     * Does not extract — called before the job directory is created.
     */
    public void validateZipFile(MultipartFile file) {
        long maxBytes = props.file().maxSizeMb() * 1024L * 1024L;

        if (file.getSize() > maxBytes) {
            throw new ValidationException("File too large. Maximum allowed size is "
                    + props.file().maxSizeMb() + " MB.");
        }

        try (InputStream in = file.getInputStream()) {
            byte[] magic = in.readNBytes(4);
            if (magic.length < 4 || !Arrays.equals(magic, ZIP_MAGIC)) {
                throw new ValidationException("File does not appear to be a valid zip archive.");
            }
        } catch (ValidationException e) {
            throw e;
        } catch (IOException e) {
            log.error("Error reading zip file for validation", e);
            throw new ValidationException("Error reading file for validation: " + e.getMessage());
        }
    }

    /**
     * Extracts the zip into jobDir/project/, locates the target blend file,
     * scans it for absolute path warnings, and returns the absolute path to the blend file.
     *
     * Zip bomb protection: aborts if uncompressed bytes exceed the configured limit
     * or the entry count exceeds the configured maximum.
     *
     * Zip slip protection: every entry path is validated to stay within jobDir before writing.
     *
     * @param file           the uploaded zip
     * @param blendFileName  filename of the blend to render (e.g. "light.blend")
     * @param jobDir         the job directory to extract into
     * @return               ExtractionResult containing the blend path and any warnings
     */
    public ExtractionResult extractAndLocate(MultipartFile file, String blendFileName, Path jobDir)
            throws IOException {

        long maxUncompressedBytes = props.zip().maxUncompressedMb() * 1024L * 1024L;
        int  maxEntries           = props.zip().maxEntries();

        Path projectDir = jobDir.resolve("project");
        Files.createDirectories(projectDir);

        Path projectDirAbs = projectDir.toAbsolutePath().normalize();

        long totalBytesWritten = 0;
        int  entryCount        = 0;
        Path blendFilePath     = null;
        String targetName      = blendFileName.toLowerCase(java.util.Locale.ROOT);

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(file.getInputStream()))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                entryCount++;
                if (entryCount > maxEntries) {
                    deleteQuietly(jobDir);
                    throw new ValidationException(
                            "Zip contains too many entries (limit: " + maxEntries + ").");
                }

                // ── Zip slip guard ──────────────────────────────────────────────────
                Path entryPath = projectDirAbs.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(projectDirAbs)) {
                    deleteQuietly(jobDir);
                    log.error("Zip slip attempt detected: entry '{}' in job {}", entry.getName(), jobDir.getFileName());
                    throw new ValidationException("Invalid zip: path traversal detected.");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                    zis.closeEntry();
                    continue;
                }

                // Ensure parent directories exist (zip may omit directory entries)
                Files.createDirectories(entryPath.getParent());

                // ── Write with byte counter (zip bomb protection) ───────────────────
                try (OutputStream out = Files.newOutputStream(entryPath)) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = zis.read(buf)) != -1) {
                        totalBytesWritten += read;
                        if (totalBytesWritten > maxUncompressedBytes) {
                            deleteQuietly(jobDir);
                            throw new ValidationException(
                                    "Zip uncompressed size exceeds the limit of "
                                    + props.zip().maxUncompressedMb() + " MB. "
                                    + "Ensure your project does not include large caches or baked simulations.");
                        }
                        out.write(buf, 0, read);
                    }
                }

                // ── Check if this is the target blend file ──────────────────────────
                if (blendFilePath == null
                        && entryPath.getFileName().toString()
                                    .toLowerCase(java.util.Locale.ROOT)
                                    .equals(targetName)) {
                    blendFilePath = entryPath;
                }

                zis.closeEntry();
            }
        }

        if (blendFilePath == null) {
            deleteQuietly(jobDir);
            throw new ValidationException(
                    "Could not find '" + blendFileName + "' inside the zip. "
                    + "Check the filename and try again.");
        }

        log.info("Zip extracted for job {} — {} entries, {} MB uncompressed, blend at {}",
                jobDir.getFileName(),
                entryCount,
                String.format("%.1f", totalBytesWritten / (1024.0 * 1024.0)),
                blendFilePath.getFileName());

        List<String> warnings = scanForAbsolutePaths(blendFilePath);

        return new ExtractionResult(blendFilePath, warnings);
    }

    // ============================
    // ABSOLUTE PATH SCANNING
    // ============================

    /**
     * Reads the blend file as raw bytes and scans for known absolute path patterns.
     * Blend files are binary but store path strings as plain ASCII/UTF-8,
     * so a byte-level scan is sufficient and avoids any parsing dependency.
     *
     * Returns a list of human-readable warning strings, empty if nothing suspicious found.
     */
    List<String> scanForAbsolutePaths(Path blendFile) {
        try {
            byte[] bytes = Files.readAllBytes(blendFile);
            String content = new String(bytes, StandardCharsets.ISO_8859_1);

            List<String> found = new ArrayList<>();
            for (String pattern : ABSOLUTE_PATH_PATTERNS) {
                if (content.contains(pattern)) {
                    found.add(pattern);
                }
            }

            if (!found.isEmpty()) {
                log.warn("Possible absolute paths detected in {}: {}", blendFile.getFileName(), found);
                return List.of(
                        "Possible absolute paths detected in " + blendFile.getFileName().toString()
                        + " (" + String.join(", ", found) + "). "
                        + "If assets appear missing in the render, re-save the project with relative paths "
                        + "(Blender: File → External Data → Make Paths Relative) and resubmit."
                );
            }
        } catch (IOException e) {
            log.warn("Could not scan blend file for absolute paths: {}", e.getMessage());
        }
        return List.of();
    }

    // ============================
    // HELPERS
    // ============================

    private void deleteQuietly(Path dir) {
        try {
            storageService.deleteDirectory(dir);
        } catch (IOException e) {
            log.warn("Could not clean up job directory {} after failed extraction: {}", dir, e.getMessage());
        }
    }

    // ============================
    // RESULT TYPE
    // ============================

    /**
     * Result of a successful extraction: the resolved blend file path and any warnings.
     */
    public record ExtractionResult(Path blendFilePath, List<String> warnings) {}
}
