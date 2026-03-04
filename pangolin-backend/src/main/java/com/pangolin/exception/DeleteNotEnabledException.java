/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.exception;

/** Thrown when a delete request arrives but pangolin.delete.enabled=false. Maps to HTTP 403. */
public class DeleteNotEnabledException extends RuntimeException {
    public DeleteNotEnabledException() {
        super("Delete feature is not enabled on this instance.");
    }
}