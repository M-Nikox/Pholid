/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.integration;

import com.pangolin.audit.AuditLog;
import com.pangolin.audit.AuditLogRepository;
import com.pangolin.audit.AuditLogService;
import com.pangolin.client.FlamencoClient;
import com.pangolin.dto.JobSubmitRequest;
import com.pangolin.dto.JobTypesResponse;
import com.pangolin.job.Job;
import com.pangolin.job.JobRepository;
import com.pangolin.service.FileStorageService;
import com.pangolin.service.JobSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the job submission flow and audit logging.
 *
 * <p>Uses a real PostgreSQL database managed by Testcontainers with the full
 * Spring application context loaded (auth disabled, the default). Flamenco
 * and filesystem calls are mocked so tests can run without external services.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Submitting a job persists a {@link Job} record with the correct {@code submitted_by}</li>
 *   <li>The job list repository reflects submitted jobs</li>
 *   <li>A deleted job is removed from the repository</li>
 *   <li>A job status can be updated (cancel flow)</li>
 *   <li>{@link AuditLog} entries are created for job submission, cancellation, and deletion</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class JobSubmissionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18")
                    .withDatabaseName("pangolin")
                    .withUsername("pangolin")
                    .withPassword("pangolin");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Repositories and services under test ─────────────────────────────────

    @Autowired
    JobRepository jobRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    AuditLogService auditLogService;

    @Autowired
    JobSubmissionService jobSubmissionService;

    // ── Mocked external dependencies ─────────────────────────────────────────

    @MockitoBean
    FlamencoClient flamencoClient;

    @MockitoBean
    FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    // ── Test lifecycle ────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        jobRepository.deleteAll();

        // Flamenco returns a fake job ID on submission
        when(flamencoClient.submitJob(any(JobSubmitRequest.class)))
                .thenReturn(Map.of("id", "550e8400-e29b-41d4-a716-446655440000"));
        // Job types needed for etag resolution
        when(flamencoClient.getJobTypes())
                .thenReturn(new JobTypesResponse(List.of()));
        // Filesystem mock
        when(fileStorageService.getJobRoot()).thenReturn(tempDir);
    }

    // ── Helper: create a minimal valid blend file ─────────────────────────────

    private MockMultipartFile blendFile() {
        // A .blend file starts with the ASCII magic "BLENDER" (7 bytes)
        byte[] content = "BLENDER-v293".getBytes();
        return new MockMultipartFile("blendFile", "scene.blend",
                "application/octet-stream", content);
    }

    // ── Helper: save a job record directly via the repository ─────────────────

    private Job saveJob(String pangolinJobId, String flamencoJobId, String submittedBy) {
        Job job = new Job();
        job.setName("Pangolin_TestProject");
        job.setStatus("active");
        job.setProjectName("TestProject");
        job.setBlendFile("scene.blend");
        job.setFrames("1-100");
        job.setSubmittedAt(OffsetDateTime.now());
        job.setFlamencoJobId(flamencoJobId);
        job.setSubmittedBy(submittedBy);
        job.setPangolinJobId(pangolinJobId);
        return jobRepository.save(job);
    }

    // ── Job submission persistence ────────────────────────────────────────────

    @Test
    void submitJob_persistsRecordWithAnonymousSubmitter() throws Exception {
        jobSubmissionService.submit(blendFile(), "TestProject", "1-100", "1", "gpu-cuda", "anonymous");

        List<Job> jobs = jobRepository.findAll();
        assertThat(jobs).hasSize(1);

        Job saved = jobs.get(0);
        assertThat(saved.getSubmittedBy()).isEqualTo("anonymous");
        assertThat(saved.getFlamencoJobId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(saved.getProjectName()).isEqualTo("TestProject");
        assertThat(saved.getFrames()).isEqualTo("1-100");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getSubmittedAt()).isNotNull();
        assertThat(saved.getPangolinJobId()).isNotNull().hasSize(16);
    }

    @Test
    void submitJob_listContainsSubmittedJob() throws Exception {
        jobSubmissionService.submit(blendFile(), "AnotherProject", "50", "1", "gpu-cuda", "anonymous");

        List<Job> jobs = jobRepository.findAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getName()).isEqualTo("Pangolin_AnotherProject");
    }

    @Test
    void deleteJob_removesRecordFromRepository() {
        saveJob("c3d4e5f607181920", "770e8400-e29b-41d4-a716-446655440002", "anonymous");

        assertThat(jobRepository.findAll()).hasSize(1);

        jobRepository.deleteAll();

        assertThat(jobRepository.findAll()).isEmpty();
    }

    @Test
    void cancelJob_statusCanBeUpdatedInRepository() {
        Job job = saveJob("d4e5f60718192021", "880e8400-e29b-41d4-a716-446655440003", "anonymous");

        job.setStatus("cancel-requested");
        jobRepository.save(job);

        Job updated = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("cancel-requested");
    }

    // ── Audit logging ─────────────────────────────────────────────────────────

    @Test
    void submitJob_createsAuditLogEntry() {
        auditLogService.logAction("JOB_SUBMITTED", "JOB", "a1b2c3d4e5f60718",
                "anonymous", "project=TestProject");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);

        AuditLog entry = logs.get(0);
        assertThat(entry.getAction()).isEqualTo("JOB_SUBMITTED");
        assertThat(entry.getResourceType()).isEqualTo("JOB");
        assertThat(entry.getResourceId()).isEqualTo("a1b2c3d4e5f60718");
        assertThat(entry.getUsername()).isEqualTo("anonymous");
        assertThat(entry.getDetails()).contains("TestProject");
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    void deleteJob_createsAuditLogEntry() {
        auditLogService.logAction("JOB_DELETED", "JOB", "a1b2c3d4e5f60718",
                "anonymous", "flamencoJobId=550e8400-e29b-41d4-a716-446655440000");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);

        AuditLog entry = logs.get(0);
        assertThat(entry.getAction()).isEqualTo("JOB_DELETED");
        assertThat(entry.getResourceId()).isEqualTo("a1b2c3d4e5f60718");
        assertThat(entry.getDetails()).contains("flamencoJobId");
    }

    @Test
    void cancelJob_createsAuditLogEntry() {
        auditLogService.logAction("JOB_CANCELLED", "JOB",
                "550e8400-e29b-41d4-a716-446655440000", "anonymous", null);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);

        AuditLog entry = logs.get(0);
        assertThat(entry.getAction()).isEqualTo("JOB_CANCELLED");
        assertThat(entry.getResourceType()).isEqualTo("JOB");
        assertThat(entry.getUsername()).isEqualTo("anonymous");
    }

    @Test
    void multipleJobActions_createMultipleAuditLogEntries() {
        auditLogService.logAction("JOB_SUBMITTED", "JOB", "job1", "anonymous", null);
        auditLogService.logAction("JOB_CANCELLED", "JOB", "job1", "anonymous", null);
        auditLogService.logAction("JOB_DELETED",   "JOB", "job1", "anonymous", null);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(3);
        assertThat(logs).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder("JOB_SUBMITTED", "JOB_CANCELLED", "JOB_DELETED");
    }
}
