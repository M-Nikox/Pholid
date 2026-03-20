CREATE TABLE jobs (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    project_name   VARCHAR(100),
    blend_file     TEXT,
    frames         VARCHAR(255),
    submitted_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    flamenco_job_id VARCHAR(255) UNIQUE,
    CONSTRAINT pk_jobs PRIMARY KEY (id)
);

CREATE INDEX idx_jobs_status ON jobs (status);
CREATE INDEX idx_jobs_submitted_at ON jobs (submitted_at DESC);
