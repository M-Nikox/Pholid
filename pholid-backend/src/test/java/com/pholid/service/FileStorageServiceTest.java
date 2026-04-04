/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.config.PholidProperties;
import com.pholid.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private static final String TEMP_ZIP_PREFIX = "pholid-render-";

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        PholidProperties props = new PholidProperties(
                new PholidProperties.Manager("http://flamenco-manager:8080"),
                new PholidProperties.Storage(tempDir.toString()),
                new PholidProperties.Frames(3500, 100000),
                new PholidProperties.Download(5000),
                new PholidProperties.ProjectName(100),
                new PholidProperties.File(512),
                new PholidProperties.Http(10000, 30000),
                new PholidProperties.Delete(false),
                new PholidProperties.Zip(2048, 10000)
        );
        service = new FileStorageService(props);
    }

    // Path traversal protection

    @Test
    void validJobId_resolvesWithinJobRoot() {
        // Compare string representations to avoid toRealPath() requiring the dir to exist
        Path result = service.resolveAndValidateJobDir("a1b2c3d4e5f60718");
        Path jobRoot = service.getJobRoot().toAbsolutePath().normalize();
        assertThat(result.toAbsolutePath().normalize().toString())
                .startsWith(jobRoot.toString());
    }

    @Test
    void pathTraversalWithDotDot_throwsValidationException() {
        assertThatThrownBy(() -> service.resolveAndValidateJobDir("../../../etc/passwd"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("path resolution failed");
    }

    @Test
    void pathTraversalAbsolute_throwsValidationException() {
        assertThatThrownBy(() -> service.resolveAndValidateJobDir("/etc/passwd"))
                .isInstanceOf(ValidationException.class);
    }

    // getStatus

    @Test
    void getStatus_noOutputDir_returnsNotReady() {
        Map<String, Object> status = service.getStatus("nonexistentjob00");
        assertThat(status.get("ready")).isEqualTo(false);
        assertThat(status.get("fileCount")).isEqualTo(0L);
    }

    @Test
    void getStatus_emptyOutputDir_returnsReadyWithZeroFiles() throws IOException {
        Path outputDir = service.getJobRoot().resolve("a1b2c3d4e5f60718").resolve("output");
        Files.createDirectories(outputDir);

        Map<String, Object> status = service.getStatus("a1b2c3d4e5f60718");
        assertThat(status.get("ready")).isEqualTo(true);
        assertThat(status.get("fileCount")).isEqualTo(0L);
    }

    @Test
    void getStatus_withPngFiles_returnsCorrectCount() throws IOException {
        Path outputDir = service.getJobRoot().resolve("a1b2c3d4e5f60718").resolve("output");
        Files.createDirectories(outputDir);
        Files.createFile(outputDir.resolve("000001.png"));
        Files.createFile(outputDir.resolve("000002.png"));
        Files.createFile(outputDir.resolve("000003.png"));
        Files.createFile(outputDir.resolve("render.log")); // non-PNG, should not count

        Map<String, Object> status = service.getStatus("a1b2c3d4e5f60718");
        assertThat(status.get("ready")).isEqualTo(true);
        assertThat(status.get("fileCount")).isEqualTo(3L);
    }

    // streamZip

    @Test
    void streamZip_success_returnsZipAndCleansTempFile() throws IOException {
        String jobId = "a1b2c3d4e5f60718";
        Path outputDir = service.getJobRoot().resolve(jobId).resolve("output");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("000001.png"), "frame-1");
        Files.writeString(outputDir.resolve("000002.png"), "frame-2");

        int before = countTempZipFiles(jobId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.streamZip(jobId, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/zip");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"render_" + jobId + ".zip\"");

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(response.getContentAsByteArray()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).containsExactlyInAnyOrder("000001.png", "000002.png");
        assertThat(countTempZipFiles(jobId)).isEqualTo(before);
    }

    @Test
    void streamZip_zipCreationFailure_returns500AndCleansTempFile() throws IOException {
        Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions are required for this test");

        String jobId = "b1b2c3d4e5f60718";
        Path outputDir = service.getJobRoot().resolve(jobId).resolve("output");
        Files.createDirectories(outputDir);
        Path unreadable = outputDir.resolve("000001.png");
        Files.writeString(unreadable, "frame-1");
        Files.setPosixFilePermissions(unreadable, Set.of());

        int before = countTempZipFiles(jobId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            service.streamZip(jobId, response);
        } finally {
            Files.setPosixFilePermissions(unreadable, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        }

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage())
                .isEqualTo("Failed to create download archive (file read/write error).");
        assertThat(countTempZipFiles(jobId)).isEqualTo(before);
    }

    // deleteDirectory

    @Test
    void deleteDirectory_removesAllContents() throws IOException {
        Path jobDir = tempDir.resolve("testjob");
        Path subDir = jobDir.resolve("output");
        Files.createDirectories(subDir);
        Files.createFile(subDir.resolve("frame001.png"));
        Files.createFile(subDir.resolve("frame002.png"));

        service.deleteDirectory(jobDir);

        assertThat(jobDir).doesNotExist();
    }

    @Test
    void deleteDirectory_emptyDir_succeeds() throws IOException {
        Path emptyDir = tempDir.resolve("emptydir");
        Files.createDirectories(emptyDir);

        service.deleteDirectory(emptyDir);

        assertThat(emptyDir).doesNotExist();
    }

    private int countTempZipFiles(String jobId) throws IOException {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        String filePrefix = TEMP_ZIP_PREFIX + jobId + "-";
        try (var files = Files.list(tmpDir)) {
            return (int) files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(filePrefix) && name.endsWith(".zip"))
                    .count();
        }
    }
}
