/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.client.FlamencoClient;
import com.pholid.config.PholidProperties;
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
        PholidProperties props = new PholidProperties(
                new PholidProperties.Manager("http://flamenco-manager:8080"),
                new PholidProperties.Storage("/shared"),
                new PholidProperties.Frames(3500, 100000),
                new PholidProperties.Download(5000),
                new PholidProperties.ProjectName(20),
                new PholidProperties.File(512),
                new PholidProperties.Http(10000, 30000),
                new PholidProperties.Delete(false),
                new PholidProperties.Zip(2048, 10000)
        );
        FileStorageService mockStorage = mock(FileStorageService.class);
        ZipSubmissionService zipService = new ZipSubmissionService(props, mockStorage);
        service = new JobSubmissionService(
                mock(FlamencoClient.class),
                mockStorage,
                zipService,
                props
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
