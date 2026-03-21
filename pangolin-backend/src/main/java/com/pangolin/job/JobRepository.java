/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
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

    Optional<Job> findByFlamencoJobId(String flamencoJobId);

    /** Returns active jobs (non-terminal status) for quota checking. */
    @Query("SELECT j FROM Job j WHERE j.submittedBy = :submittedBy AND j.status NOT IN ('completed', 'failed', 'canceled')")
    List<Job> findActiveJobsBySubmittedBy(@Param("submittedBy") String submittedBy);

    /** Counts jobs submitted by a user since a given time (for rate limiting). */
    @Query("SELECT COUNT(j) FROM Job j WHERE j.submittedBy = :submittedBy AND j.submittedAt >= :since")
    long countJobsBySubmittedBySince(@Param("submittedBy") String submittedBy,
                                     @Param("since") java.time.OffsetDateTime since);

    /** Returns all jobs with non-terminal status (for progress polling). */
    @Query("SELECT j FROM Job j WHERE j.status NOT IN ('completed', 'failed', 'canceled') AND j.flamencoJobId IS NOT NULL")
    List<Job> findActiveJobsWithFlamencoId();

    /** Paginated terminal job history for a specific user, newest first. */
    @Query("SELECT j FROM Job j WHERE j.status IN ('completed', 'failed', 'canceled') AND j.submittedBy = :submittedBy ORDER BY j.submittedAt DESC")
    Page<Job> findHistoryByUser(@Param("submittedBy") String submittedBy, Pageable pageable);

    /** Deletes a job record by its Flamenco job ID. */
    @Modifying
    @Query("DELETE FROM Job j WHERE j.flamencoJobId = :flamencoJobId")
    int deleteByFlamencoJobId(@Param("flamencoJobId") String flamencoJobId);

    /** Updates status for a job identified by Flamenco ID. */
    @Modifying
    @Query("UPDATE Job j SET j.status = :status WHERE j.flamencoJobId = :flamencoJobId")
    int updateStatusByFlamencoJobId(@Param("flamencoJobId") String flamencoJobId,
                                    @Param("status") String status);

    /** Paginated terminal job history across all users (admin view), newest first. */
    @Query("SELECT j FROM Job j WHERE j.status IN ('completed', 'failed', 'canceled') ORDER BY j.submittedAt DESC")
    Page<Job> findAllHistory(Pageable pageable);
}
