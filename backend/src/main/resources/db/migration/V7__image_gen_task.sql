CREATE TABLE IF NOT EXISTS image_gen_task (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    char_id UUID,
    task_type VARCHAR(30) NOT NULL,
    external_task_id VARCHAR(100),
    prompt TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    callback_key VARCHAR(500),
    result_url VARCHAR(1000),
    error_message TEXT,
    poll_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_igt_status ON image_gen_task(status);
CREATE INDEX IF NOT EXISTS idx_igt_project ON image_gen_task(project_id);
