/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.client;

import com.pangolin.dto.JobSetStatusRequest;
import com.pangolin.dto.JobSubmitRequest;
import com.pangolin.dto.JobTypesResponse;
import com.pangolin.dto.TaskLogMeta;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

/**
 * Declarative HTTP client for Flamenco Manager's /api/v3 API.
 * Spring generates the implementation via RestClient + HttpServiceProxyFactory (AppConfig).
 *
 * Responses that are purely proxied to the frontend use Map<String, Object> to avoid
 * dropping fields Flamenco may add in future versions. Typed DTOs are used where we
 * actually inspect response fields in service code (JobTypesResponse, TaskLogMeta),
 * or send structured request bodies (JobSubmitRequest, JobSetStatusRequest).
 */
@HttpExchange("/api/v3")
public interface FlamencoClient {

    @GetExchange("/status")
    Map<String, Object> getFarmStatus();

    @GetExchange("/jobs")
    Map<String, Object> getJobs(
            @RequestParam("status_in") String statusIn,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    );

    @GetExchange("/jobs/types")
    JobTypesResponse getJobTypes();

    @GetExchange("/jobs/{jobId}")
    Map<String, Object> getJob(@PathVariable String jobId);

    @PostExchange("/jobs")
    Map<String, Object> submitJob(@RequestBody JobSubmitRequest request);

    @PostExchange("/jobs/{jobId}/setstatus")
    void setJobStatus(@PathVariable String jobId, @RequestBody JobSetStatusRequest request);

    @DeleteExchange("/jobs/{jobId}")
    void deleteJob(@PathVariable String jobId);

    @GetExchange("/jobs/{jobId}/tasks")
    Map<String, Object> getJobTasks(@PathVariable String jobId);

    @GetExchange("/tasks/{taskId}/log")
    TaskLogMeta getTaskLog(@PathVariable String taskId);
}