/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.job;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code jobs} table (created by V1 Flyway migration,
 * extended by V3 to add {@code submitted_by} and {@code pholid_job_id}).
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "project_name", length = 100)
    private String projectName;

    @Column(name = "blend_file", columnDefinition = "TEXT")
    private String blendFile;

    @Column(name = "frames")
    private String frames;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "flamenco_job_id", unique = true)
    private String flamencoJobId;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "pholid_job_id", unique = true, length = 32)
    private String pholidJobId;

    @Column(name = "progress", nullable = false)
    private int progress = 0;

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getBlendFile() { return blendFile; }
    public void setBlendFile(String blendFile) { this.blendFile = blendFile; }

    public String getFrames() { return frames; }
    public void setFrames(String frames) { this.frames = frames; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public String getFlamencoJobId() { return flamencoJobId; }
    public void setFlamencoJobId(String flamencoJobId) { this.flamencoJobId = flamencoJobId; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public String getPholidJobId() { return pholidJobId; }
    public void setPholidJobId(String pholidJobId) { this.pholidJobId = pholidJobId; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}
