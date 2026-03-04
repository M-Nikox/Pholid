/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.controller;

import com.pangolin.audit.AuditLogService;
import com.pangolin.client.FlamencoClient;
import com.pangolin.config.PangolinProperties;
import com.pangolin.service.FileStorageService;
import com.pangolin.service.JobSubmissionService;
import com.pangolin.service.UserContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Slice test for the delete status gate in RenderController.
 * Only loads the MVC layer, no full application context.
 */
@WebMvcTest(RenderController.class)
class RenderControllerDeleteTest {

    private static final String VALID_FLAMENCO_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_PANGOLIN_ID  = "a1b2c3d4e5f60718";

    @Autowired MockMvcTester mvc;

    @MockitoBean FlamencoClient    flamencoClient;
    @MockitoBean FileStorageService storageService;
    @MockitoBean JobSubmissionService submissionService;
    @MockitoBean PangolinProperties props;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean UserContextService userContextService;

    // ── Delete not enabled ──────────────────────────────────────────────────

    @Test
    void deleteNotEnabled_returns403() {
        when(props.delete()).thenReturn(new PangolinProperties.Delete(false));

        assertThat(mvc.delete().uri("/api/render/job/{id}", VALID_FLAMENCO_ID))
                .hasStatus(403);
    }

    // ── Invalid ID ──────────────────────────────────────────────────────────

    @Test
    void invalidFlamencoId_returns400() {
        when(props.delete()).thenReturn(new PangolinProperties.Delete(true));

        assertThat(mvc.delete().uri("/api/render/job/{id}", "not-a-valid-uuid"))
                .hasStatus(400);
    }

    // ── Status gate ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"completed", "failed", "canceled"})
    void terminalStatus_returns200(String status) {
        enableDelete();
        mockJobWithStatus(status);
        mockStoragePath();
        doNothing().when(flamencoClient).deleteJob(any());

        assertThat(mvc.delete().uri("/api/render/job/{id}", VALID_FLAMENCO_ID))
                .hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "queued", "paused", "cancel-requested"})
    void nonTerminalStatus_returns409(String status) {
        enableDelete();
        mockJobWithStatus(status);

        assertThat(mvc.delete().uri("/api/render/job/{id}", VALID_FLAMENCO_ID))
                .hasStatus(409);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void enableDelete() {
        when(props.delete()).thenReturn(new PangolinProperties.Delete(true));
        when(userContextService.canAccessByFlamencoId(any())).thenReturn(true);
    }

    private void mockJobWithStatus(String status) {
        Map<String, Object> job = Map.of(
                "status",   status,
                "metadata", Map.of("pangolin.job_id", VALID_PANGOLIN_ID)
        );
        when(flamencoClient.getJob(VALID_FLAMENCO_ID)).thenReturn(job);
    }

    private void mockStoragePath() {
        Path fakePath = Path.of("/shared/jobs", VALID_PANGOLIN_ID);
        when(storageService.resolveAndValidateJobDir(VALID_PANGOLIN_ID)).thenReturn(fakePath);
        when(storageService.getJobRoot()).thenReturn(Path.of("/shared/jobs"));
    }
}
