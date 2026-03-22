/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.notification;

import com.pholid.config.PholidProperties;
import com.pholid.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Delivers webhook notifications when a render job reaches a terminal status.
 *
 * <p>Email (SMTP) is intentionally omitted for v2. Add back in v3:
 * re-introduce Optional<JavaMailSender>, restore the Mail record in PholidProperties,
 * and add SMTP env vars to compose.
 *
 * <p>All delivery is best-effort — errors are logged but never propagate to callers.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final PholidProperties props;
    private final NotificationPreferenceRepository preferenceRepository;
    private final RestClient webhookClient;

    public NotificationService(PholidProperties props,
                                NotificationPreferenceRepository preferenceRepository) {
        this.props                = props;
        this.preferenceRepository = preferenceRepository;
        this.webhookClient        = RestClient.create();
    }

    /**
     * Triggers notifications for the given job reaching a terminal status.
     * Looks up the user's preferences and delivers according to their settings.
     *
     * @param job       the completed/failed job
     * @param newStatus the terminal status ("completed", "failed", or "canceled")
     */
    public void notifyJobStatus(Job job, String newStatus) {
        String username = job.getSubmittedBy() != null ? job.getSubmittedBy() : "anonymous";

        NotificationPreference prefs = preferenceRepository.findByUsername(username)
                .orElse(null);

        boolean shouldNotify = switch (newStatus) {
            case "completed" -> prefs == null || prefs.isNotifyOnComplete();
            case "failed"    -> prefs == null || prefs.isNotifyOnFailure();
            default          -> false;
        };

        if (!shouldNotify) return;

        sendWebhook(job, newStatus, prefs);
    }

    // ── Webhook ───────────────────────────────────────────────────────────────

    private void sendWebhook(Job job, String status, NotificationPreference prefs) {
        // Determine the webhook URL: per-user preference takes priority, fall back to global
        String webhookUrl = null;
        if (prefs != null && prefs.isWebhookEnabled()
                && prefs.getWebhookUrl() != null && !prefs.getWebhookUrl().isBlank()) {
            webhookUrl = prefs.getWebhookUrl();
        } else if (isGlobalWebhookConfigured()) {
            webhookUrl = props.webhook().url();
        }

        if (webhookUrl == null) return;

        final String url = webhookUrl;
        try {
            Map<String, Object> payload = Map.of(
                    "jobId",         job.getPholidJobId() != null ? job.getPholidJobId() : "",
                    "flamencoJobId", job.getFlamencoJobId() != null ? job.getFlamencoJobId() : "",
                    "status",        status,
                    "project",       job.getProjectName() != null ? job.getProjectName() : "",
                    "submittedBy",   job.getSubmittedBy() != null ? job.getSubmittedBy() : "anonymous",
                    "completedAt",   job.getCompletedAt() != null ? job.getCompletedAt().toString() : ""
            );

            webhookClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook notification sent to {} for job {} ({})",
                    url, job.getPholidJobId(), status);
        } catch (Exception e) {
            log.warn("Failed to send webhook notification for job {} to {}: {}",
                    job.getPholidJobId(), url, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────


    private boolean isGlobalWebhookConfigured() {
        return props.webhook() != null
                && props.webhook().url() != null
                && !props.webhook().url().isBlank();
    }
}
