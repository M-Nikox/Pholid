/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.dto;

/** Request body for POST /api/v3/jobs/{jobId}/setstatus. */
public record JobSetStatusRequest(String status, String reason) {}