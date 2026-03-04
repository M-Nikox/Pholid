/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.notification;

import com.pangolin.config.PangolinProperties;
import com.pangolin.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Service that delivers email and/or webhook notifications when a render job
 * reaches a terminal status (completed or failed).
 *
 * <p>All delivery is best-effort — errors are logged but never propagate to callers.
 * Notifications are skipped when:
 * <ul>
 *   <li>The user has not enabled that notification type in their preferences.</li>
 *   <li>The required infrastructure is not configured (empty SMTP host or webhook URL).</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final PangolinProperties props;
    private final NotificationPreferenceRepository preferenceRepository;
    private final Optional<JavaMailSender> mailSender;
    /** Dedicated client for outbound webhook POSTs (not the Flamenco manager client). */
    private final RestClient webhookClient;

    public NotificationService(PangolinProperties props,
                                NotificationPreferenceRepository preferenceRepository,
                                Optional<JavaMailSender> mailSender) {
        this.props                = props;
        this.preferenceRepository = preferenceRepository;
        this.mailSender           = mailSender;
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

        sendEmail(job, newStatus, prefs);
        sendWebhook(job, newStatus, prefs);
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private void sendEmail(Job job, String status, NotificationPreference prefs) {
        if (prefs == null || !prefs.isEmailEnabled()) return;
        String to = prefs.getEmailAddress();
        if (to == null || to.isBlank()) return;

        if (!isSmtpConfigured()) {
            log.debug("SMTP not configured — skipping email notification for job {}", job.getPangolinJobId());
            return;
        }

        mailSender.ifPresent(sender -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(props.mail().from());
                msg.setTo(to);
                msg.setSubject("Pangolin: Render job " + status + " — " + job.getProjectName());
                msg.setText(buildEmailBody(job, status));
                sender.send(msg);
                log.info("Email notification sent to {} for job {} ({})",
                        to, job.getPangolinJobId(), status);
            } catch (Exception e) {
                log.warn("Failed to send email notification for job {}: {}",
                        job.getPangolinJobId(), e.getMessage());
            }
        });
    }

    private String buildEmailBody(Job job, String status) {
        return String.format(
                "Your render job has %s.%n%n" +
                "Job ID:       %s%n" +
                "Project:      %s%n" +
                "Frames:       %s%n" +
                "Status:       %s%n" +
                (job.getCompletedAt() != null ? "Completed at: " + job.getCompletedAt() + "%n" : "") +
                "%nPangolin Render Manager",
                status,
                job.getPangolinJobId(),
                job.getProjectName(),
                job.getFrames(),
                status
        );
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
                    "jobId",         job.getPangolinJobId() != null ? job.getPangolinJobId() : "",
                    "flamencoJobId", job.getFlamencoJobId() != null ? job.getFlamencoJobId() : "",
                    "status",        status,
                    "project",       job.getProjectName() != null ? job.getProjectName() : "",
                    "submittedBy",   job.getSubmittedBy() != null ? job.getSubmittedBy() : "anonymous",
                    "completedAt",   job.getCompletedAt() != null ? job.getCompletedAt().toString() : ""
            );

            webhookClient.post()
                    .uri(url)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook notification sent to {} for job {} ({})",
                    url, job.getPangolinJobId(), status);
        } catch (Exception e) {
            log.warn("Failed to send webhook notification for job {} to {}: {}",
                    job.getPangolinJobId(), url, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isSmtpConfigured() {
        return props.mail() != null
                && props.mail().host() != null
                && !props.mail().host().isBlank();
    }

    private boolean isGlobalWebhookConfigured() {
        return props.webhook() != null
                && props.webhook().url() != null
                && !props.webhook().url().isBlank();
    }
}
