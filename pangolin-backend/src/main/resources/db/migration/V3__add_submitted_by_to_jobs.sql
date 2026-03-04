ALTER TABLE jobs ADD COLUMN submitted_by   VARCHAR(255);
ALTER TABLE jobs ADD COLUMN pangolin_job_id VARCHAR(32) UNIQUE;

UPDATE jobs SET submitted_by = 'anonymous' WHERE submitted_by IS NULL;
