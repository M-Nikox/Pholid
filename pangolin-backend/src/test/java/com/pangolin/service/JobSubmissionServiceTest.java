/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.service;

import com.pangolin.client.FlamencoClient;
import com.pangolin.config.PangolinProperties;
import com.pangolin.job.JobRepository;
import com.pangolin.model.FrameValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JobSubmissionServiceTest {

    private JobSubmissionService service;

    @BeforeEach
    void setUp() {
        PangolinProperties props = new PangolinProperties(
                new PangolinProperties.Manager("http://flamenco-manager:8080"),
                new PangolinProperties.Storage("/shared"),
                new PangolinProperties.Frames(3500, 100000),
                new PangolinProperties.Download(5000),
                new PangolinProperties.ProjectName(100),
                new PangolinProperties.File(512),
                new PangolinProperties.Http(10000, 30000),
                new PangolinProperties.Delete(false),
                new PangolinProperties.Auth(false, "pangolin-admins")
        );
        service = new JobSubmissionService(
                mock(FlamencoClient.class),
                mock(FileStorageService.class),
                props,
                mock(JobRepository.class)
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
}
