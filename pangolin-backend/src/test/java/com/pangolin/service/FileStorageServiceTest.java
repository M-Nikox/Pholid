/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.service;

import com.pangolin.config.PangolinProperties;
import com.pangolin.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        PangolinProperties props = new PangolinProperties(
                new PangolinProperties.Manager("http://flamenco-manager:8080"),
                new PangolinProperties.Storage(tempDir.toString()),
                new PangolinProperties.Frames(3500, 100000),
                new PangolinProperties.Download(5000),
                new PangolinProperties.ProjectName(100),
                new PangolinProperties.File(512),
                new PangolinProperties.Http(10000, 30000),
                new PangolinProperties.Delete(false)
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
}
