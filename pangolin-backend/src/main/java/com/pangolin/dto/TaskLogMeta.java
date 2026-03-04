/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.dto;

/**
 * Response from GET /api/v3/tasks/{taskId}/log.
 * Contains the relative path to the log file served by Flamenco Manager.
 */
public record TaskLogMeta(String url) {}