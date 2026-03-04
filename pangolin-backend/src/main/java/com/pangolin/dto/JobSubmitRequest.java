/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request body for POST /api/v3/jobs.
 * typeEtag is optional and omitted from serialised JSON when null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobSubmitRequest(
        String name,
        String type,
        int priority,
        @JsonProperty("submitter_platform") String submitterPlatform,
        @JsonProperty("type_etag") String typeEtag,
        Map<String, String> metadata,
        Map<String, Object> settings
) {}