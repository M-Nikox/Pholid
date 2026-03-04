/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Job} entries.
 */
public interface JobRepository extends JpaRepository<Job, UUID> {

    /** Returns all Flamenco job IDs submitted by the given user (non-null values only). */
    @Query("SELECT j.flamencoJobId FROM Job j WHERE j.submittedBy = :submittedBy AND j.flamencoJobId IS NOT NULL")
    Set<String> findFlamencoJobIdsBySubmittedBy(@Param("submittedBy") String submittedBy);

    boolean existsByFlamencoJobIdAndSubmittedBy(String flamencoJobId, String submittedBy);

    boolean existsByPangolinJobIdAndSubmittedBy(String pangolinJobId, String submittedBy);
}
