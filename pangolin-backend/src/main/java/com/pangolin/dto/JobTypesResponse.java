/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from GET /api/v3/jobs/types.
 * Used to extract the type_etag before job submission.
 */
public record JobTypesResponse(
        @JsonProperty("job_types") List<JobType> jobTypes
) {
    public record JobType(String name, String etag) {}
}