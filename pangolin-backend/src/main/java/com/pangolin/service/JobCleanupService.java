/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.pangolin.service;

import com.pangolin.job.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles local persistence cleanup for completed jobs.
 */
@Service
public class JobCleanupService {

    private final JobRepository jobRepository;

    public JobCleanupService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public int deleteByFlamencoJobId(String flamencoJobId) {
        return jobRepository.deleteByFlamencoJobId(flamencoJobId);
    }
}
