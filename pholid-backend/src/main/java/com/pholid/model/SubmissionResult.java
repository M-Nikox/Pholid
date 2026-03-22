/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.model;

import java.util.List;

/**
 * Result of a job submission pipeline.
 * Always contains a jobId; warnings is empty for clean submissions
 * and non-empty when potential issues were detected (e.g. absolute paths in a zip project).
 */
public record SubmissionResult(String jobId, List<String> warnings) {

    public static SubmissionResult clean(String jobId) {
        return new SubmissionResult(jobId, List.of());
    }

    public static SubmissionResult withWarnings(String jobId, List<String> warnings) {
        return new SubmissionResult(jobId, List.copyOf(warnings));
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
