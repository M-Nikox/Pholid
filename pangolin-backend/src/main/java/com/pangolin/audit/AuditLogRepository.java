/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AuditLog} entries.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
