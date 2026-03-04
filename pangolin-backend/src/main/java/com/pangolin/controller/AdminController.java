/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.controller;

import com.pangolin.audit.AuditLog;
import com.pangolin.audit.AuditLogRepository;
import com.pangolin.client.FlamencoClient;
import com.pangolin.job.Job;
import com.pangolin.job.JobRepository;
import com.pangolin.service.UserContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller for the admin panel.
 *
 * <p>The panel is only accessible to admin users. When authentication is disabled,
 * all users are considered admins (existing behaviour), so the page is accessible.
 * When authentication is enabled, access is restricted by the {@code isAdmin()} check.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserContextService userContextService;
    private final JobRepository jobRepository;
    private final AuditLogRepository auditLogRepository;
    private final FlamencoClient flamencoClient;

    public AdminController(UserContextService userContextService,
                           JobRepository jobRepository,
                           AuditLogRepository auditLogRepository,
                           FlamencoClient flamencoClient) {
        this.userContextService  = userContextService;
        this.jobRepository       = jobRepository;
        this.auditLogRepository  = auditLogRepository;
        this.flamencoClient      = flamencoClient;
    }

    @GetMapping
    public String adminPanel(
            @RequestParam(defaultValue = "0") int auditPage,
            @RequestParam(defaultValue = "50") int auditSize,
            Model model) {

        if (!userContextService.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }

        // All jobs (most recent first)
        List<Job> allJobs = jobRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getSubmittedAt().compareTo(a.getSubmittedAt()))
                .toList();

        // Audit log (paginated)
        int size = Math.min(Math.max(auditSize, 1), 200);
        int page = Math.max(auditPage, 0);
        Page<AuditLog> auditLogs = auditLogRepository.findAllByOrderByTimestampDesc(
                PageRequest.of(page, size));

        // Farm status (best-effort)
        Map<String, Object> farmStatus = null;
        try {
            farmStatus = flamencoClient.getFarmStatus();
        } catch (Exception e) {
            log.debug("Could not fetch farm status for admin panel: {}", e.getMessage());
        }

        model.addAttribute("jobs", allJobs);
        model.addAttribute("auditLogs", auditLogs);
        model.addAttribute("auditPage", page);
        model.addAttribute("auditSize", size);
        model.addAttribute("farmStatus", farmStatus);
        model.addAttribute("currentUser", userContextService.getCurrentUsername());
        model.addAttribute("isAdmin", true);

        return "admin";
    }
}
