/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.client.FlamencoClient;
import com.pholid.config.PholidProperties;
import com.pholid.model.FrameValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSubmissionServiceTest {

    @TempDir
    Path tempDir;

    private JobSubmissionService service;
    private FlamencoClient flamencoClient;
    private FileStorageService storageService;
    private ZipSubmissionService zipService;
    private Path jobRoot;

    @BeforeEach
    void setUp() {
        PholidProperties props = new PholidProperties(
                new PholidProperties.Manager("http://flamenco-manager:8080"),
                new PholidProperties.Storage("/shared"),
                new PholidProperties.Frames(3500, 100000),
                new PholidProperties.Download(5000),
                new PholidProperties.ProjectName(100),
                new PholidProperties.File(512),
                new PholidProperties.Http(10000, 30000),
                new PholidProperties.Delete(false),
                new PholidProperties.Zip(2048, 10000)
        );
        flamencoClient = mock(FlamencoClient.class);
        storageService = mock(FileStorageService.class);
        zipService = mock(ZipSubmissionService.class);
        jobRoot = tempDir.resolve("jobs");
        when(storageService.getJobRoot()).thenReturn(jobRoot);

        service = new JobSubmissionService(
                flamencoClient,
                storageService,
                zipService,
                props
        );
    }

    // ── Valid ranges ────────────────────────────────────────────────────────

    @Test
    void singleFrame_valid() {
        var result = service.validateFrames("42");
        assertThat(result).isInstanceOf(FrameValidationResult.Valid.class);
        var valid = (FrameValidationResult.Valid) result;
        assertThat(valid.start()).isEqualTo(42);
        assertThat(valid.end()).isEqualTo(42);
        assertThat(valid.totalFrames()).isEqualTo(1);
    }

    @Test
    void frameRange_valid() {
        var result = service.validateFrames("1-250");
        assertThat(result).isInstanceOf(FrameValidationResult.Valid.class);
        var valid = (FrameValidationResult.Valid) result;
        assertThat(valid.start()).isEqualTo(1);
        assertThat(valid.end()).isEqualTo(250);
        assertThat(valid.totalFrames()).isEqualTo(250);
    }

    @Test
    void minimumSingleFrame_valid() {
        var result = service.validateFrames("1");
        assertThat(result).isInstanceOf(FrameValidationResult.Valid.class);
    }

    @Test
    void exactlyAtFrameLimit_valid() {
        // limit = 3500
        var result = service.validateFrames("1-3500");
        assertThat(result).isInstanceOf(FrameValidationResult.Valid.class);
        assertThat(((FrameValidationResult.Valid) result).totalFrames()).isEqualTo(3500);
    }

    // ── Invalid ranges ──────────────────────────────────────────────────────

    @Test
    void frameZero_rejected() {
        var result = service.validateFrames("0");
        assertThat(result).isInstanceOf(FrameValidationResult.Invalid.class);
        assertThat(((FrameValidationResult.Invalid) result).error()).contains("positive");
    }

    @Test
    void negativeFrame_rejected() {
        var result = service.validateFrames("-5");
        assertThat(result).isInstanceOf(FrameValidationResult.Invalid.class);
    }

    @Test
    void reversedRange_rejected() {
        var result = service.validateFrames("100-50");
        assertThat(result).isInstanceOf(FrameValidationResult.Invalid.class);
        assertThat(((FrameValidationResult.Invalid) result).error()).contains("end frame");
    }

    @Test
    void rangeStartAtZero_rejected() {
        var result = service.validateFrames("0-100");
        assertThat(result).isInstanceOf(FrameValidationResult.Invalid.class);
    }

    @Test
    void exceedsFrameLimit_rejected() {
        // limit = 3500, requesting 3501
        var result = service.validateFrames("1-3501");
        assertThat(result).isInstanceOf(FrameValidationResult.Invalid.class);
        assertThat(((FrameValidationResult.Invalid) result).error()).contains("3500");
    }

    @Test
    void exceedsFrameCap_rejected() {
        // cap = 100000
        var result = service.validateFrames("100001");
        assertThat(result).isInstanceOf(FrameValidationResult.Invalid.class);
        assertThat(((FrameValidationResult.Invalid) result).error()).contains("100000");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "abc",
        "1.5",
        "1-2-3",
        "",
        "1 - 250",
        "1,250",
    })
    void invalidFormat_rejected(String input) {
        var result = service.validateFrames(input);
        assertThat(result).isInstanceOf(FrameValidationResult.Invalid.class);
    }

    @Test
    void submitBlend_managerFailure_rollsBackJobDirectory() throws IOException {
        MockMultipartFile blend = new MockMultipartFile(
                "file",
                "scene.blend",
                "application/octet-stream",
                "BLENDER_test".getBytes(StandardCharsets.US_ASCII)
        );
        doThrow(new RuntimeException("manager down")).when(flamencoClient).submitJob(any());

        assertThatThrownBy(() -> service.submit(blend, "proj", "1-2", "2", "cpu", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("manager down");

        verify(storageService).deleteDirectory(any(Path.class));
    }

    @Test
    void submitZip_managerFailure_rollsBackJobDirectory() throws IOException {
        MockMultipartFile zip = new MockMultipartFile(
                "file",
                "project.zip",
                "application/zip",
                new byte[]{0x50, 0x4B, 0x03, 0x04}
        );
        when(zipService.extractAndLocate(eq(zip), eq("main.blend"), any(Path.class)))
                .thenAnswer(invocation -> {
                    Path jobDir = invocation.getArgument(2);
                    Path blendPath = jobDir.resolve("project").resolve("main.blend");
                    Files.createDirectories(blendPath.getParent());
                    Files.writeString(blendPath, "blend");
                    return new ZipSubmissionService.ExtractionResult(blendPath, List.of());
                });
        doThrow(new RuntimeException("manager down")).when(flamencoClient).submitJob(any());

        assertThatThrownBy(() -> service.submit(zip, "proj", "1-2", "2", "cpu", "main.blend"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("manager down");

        verify(storageService).deleteDirectory(any(Path.class));
    }
}
