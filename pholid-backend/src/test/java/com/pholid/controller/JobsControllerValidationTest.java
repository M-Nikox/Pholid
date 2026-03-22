/*
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.controller;

import com.pholid.client.FlamencoClient;
import com.pholid.exception.ValidationException;
import com.pholid.job.JobRepository;
import com.pholid.service.UserContextService;
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
 * Tests limit/offset validation in JobsController.getJobHistory().
 */
class JobsControllerValidationTest {

    private JobsController controller;
    private FlamencoClient flamencoClient;

    @BeforeEach
    void setUp() {
        flamencoClient = mock(FlamencoClient.class);
        when(flamencoClient.getJobs(anyString(), anyInt(), anyInt()))
                .thenReturn(java.util.Map.of());

        UserContextService userContextService = mock(UserContextService.class);
        when(userContextService.isAdmin()).thenReturn(true);
        when(userContextService.getCurrentUsername()).thenReturn("testuser");

        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.findAllHistory(any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(jobRepository.findHistoryByUser(anyString(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        controller = new JobsController(flamencoClient, mock(RestClient.class),
                userContextService, jobRepository);
    }

    // ── Limit ───────────────────────────────────────────────────────────────

    @Test
    void limitAtMinimum_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getJobHistory(1, 0));
    }

    @Test
    void limitAtMaximum_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getJobHistory(200, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 201, 1000})
    void limitOutOfRange_throwsValidationException(int limit) {
        assertThatThrownBy(() -> controller.getJobHistory(limit, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("200");
    }

    // ── Offset ──────────────────────────────────────────────────────────────

    @Test
    void offsetZero_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getJobHistory(50, 0));
    }

    @Test
    void offsetPositive_accepted() {
        assertThatNoException().isThrownBy(() -> controller.getJobHistory(50, 100));
    }

    @Test
    void negativeOffset_throwsValidationException() {
        assertThatThrownBy(() -> controller.getJobHistory(50, -1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("0");
    }
}
