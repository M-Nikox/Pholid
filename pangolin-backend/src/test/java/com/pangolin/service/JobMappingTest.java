/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.service;

import com.pangolin.client.FlamencoClient;
import com.pangolin.config.PangolinProperties;
import com.pangolin.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the priority and compute mode mappings so they can't silently change.
 * Also covers project name sanitization (truncation + HTML escaping).
 */
class JobMappingTest {

    private JobSubmissionService service;

    @BeforeEach
    void setUp() {
        PangolinProperties props = new PangolinProperties(
                new PangolinProperties.Manager("http://flamenco-manager:8080"),
                new PangolinProperties.Storage("/shared"),
                new PangolinProperties.Frames(3500, 100000),
                new PangolinProperties.Download(5000),
                new PangolinProperties.ProjectName(20),
                new PangolinProperties.File(512),
                new PangolinProperties.Http(10000, 30000),
                new PangolinProperties.Delete(false),
                new PangolinProperties.Auth(false, "pangolin-admins", "admin"),
                null, null,
                new PangolinProperties.Quota(100, 1000)
        );
        service = new JobSubmissionService(
                mock(FlamencoClient.class),
                mock(FileStorageService.class),
                props,
                mock(JobRepository.class)
        );
    }

    // ── Priority mapping ────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({"1,40", "2,60", "3,80"})
    void priority_mapsCorrectly(String input, int expected) throws Exception {
        assertThat(invokePriority(input)).isEqualTo(expected);
    }

    @Test
    void unknownPriority_returnsDefault() throws Exception {
        assertThat(invokePriority("99")).isEqualTo(50);
        assertThat(invokePriority("")).isEqualTo(50);
    }

    // ── Compute mode → job type mapping ────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "gpu-optix, cycles-optix-gpu",
        "gpu-cuda,  cycles-cuda-gpu",
        "cpu,       simple-blender-render",
        "GPU-CUDA,  cycles-cuda-gpu",   // case-insensitive
        "unknown,   simple-blender-render"
    })
    void computeMode_mapsToJobType(String mode, String expected) throws Exception {
        assertThat(invokeJobType(mode.trim())).isEqualTo(expected.trim());
    }

    // ── Project name sanitization ───────────────────────────────────────────

    @Test
    void projectName_truncatedToMaxLength() throws Exception {
        String long20 = "A".repeat(25);
        String result = invokeSanitize(long20);
        assertThat(result.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void projectName_htmlEscaped() throws Exception {
        String result = invokeSanitize("<script>alert('xss')</script>");
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("</script>");
    }

    @Test
    void projectName_normalName_unchanged() throws Exception {
        assertThat(invokeSanitize("My Animation")).isEqualTo("My Animation");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private int invokePriority(String input) throws Exception {
        Method m = JobSubmissionService.class.getDeclaredMethod("calculatePriority", String.class);
        m.setAccessible(true);
        return (int) m.invoke(service, input);
    }

    private String invokeJobType(String input) throws Exception {
        Method m = JobSubmissionService.class.getDeclaredMethod("resolveJobType", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, input);
    }

    private String invokeSanitize(String input) throws Exception {
        Method m = JobSubmissionService.class.getDeclaredMethod("sanitizeProjectName", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, input);
    }
}
