/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AuditLog} entries.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Returns audit log entries ordered by timestamp descending (for admin panel). */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
