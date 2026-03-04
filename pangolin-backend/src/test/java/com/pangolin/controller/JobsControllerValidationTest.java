/*
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.controller;

import com.pangolin.client.FlamencoClient;
import com.pangolin.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests limit/offset validation in JobsController.getPreviousJobs().
 */
class JobsControllerValidationTest {

    private JobsController controller;
    private FlamencoClient flamencoClient;

    @BeforeEach
    void setUp() {
        flamencoClient = mock(FlamencoClient.class);
        when(flamencoClient.getJobs(anyString(), anyInt(), anyInt()))
                .thenReturn(java.util.Map.of());
        controller = new JobsController(flamencoClient, mock(RestClient.class));
    }

    // ── Limit ───────────────────────────────────────────────────────────────

    @Test
    void limitAtMinimum_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getPreviousJobs(1, 0));
    }

    @Test
    void limitAtMaximum_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getPreviousJobs(200, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 201, 1000})
    void limitOutOfRange_throwsValidationException(int limit) {
        assertThatThrownBy(() -> controller.getPreviousJobs(limit, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("200");
    }

    // ── Offset ──────────────────────────────────────────────────────────────

    @Test
    void offsetZero_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getPreviousJobs(50, 0));
    }

    @Test
    void offsetPositive_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getPreviousJobs(50, 100));
    }

    @Test
    void negativeOffset_throwsValidationException() {
        assertThatThrownBy(() -> controller.getPreviousJobs(50, -1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("0");
    }
}
