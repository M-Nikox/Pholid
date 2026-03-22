/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.dto;

/** Request body for POST /api/v3/jobs/{jobId}/setstatus. */
public record JobSetStatusRequest(String status, String reason) {}