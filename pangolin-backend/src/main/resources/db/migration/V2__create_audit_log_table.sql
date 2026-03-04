CREATE TABLE audit_log (
    id            BIGSERIAL    NOT NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id   VARCHAR(255),
    username      VARCHAR(255),
    timestamp     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    details       TEXT,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_log_timestamp ON audit_log (timestamp DESC);
CREATE INDEX idx_audit_log_resource ON audit_log (resource_type, resource_id);
