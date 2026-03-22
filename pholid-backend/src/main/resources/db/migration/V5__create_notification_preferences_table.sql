CREATE TABLE notification_preferences (
    id               BIGSERIAL    NOT NULL,
    username         VARCHAR(255) NOT NULL UNIQUE,
    email_enabled    BOOLEAN      NOT NULL DEFAULT false,
    email_address    VARCHAR(255),
    webhook_enabled  BOOLEAN      NOT NULL DEFAULT false,
    webhook_url      VARCHAR(1024),
    notify_on_complete BOOLEAN    NOT NULL DEFAULT true,
    notify_on_failure  BOOLEAN    NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_preferences PRIMARY KEY (id)
);

CREATE INDEX idx_notification_preferences_username ON notification_preferences (username);
