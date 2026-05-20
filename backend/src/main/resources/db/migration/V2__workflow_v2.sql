-- Flyway Migration V2: 8-step workflow upgrade
-- Splits VIDEO_COMPOSITION into VOICE_GENERATION + CLIP_GENERATION + FINAL_COMPOSITION

-- ============================================================
-- 1. Add new fields to storyboard_frame
-- ============================================================
ALTER TABLE storyboard_frame
    ADD COLUMN IF NOT EXISTS clip_video_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS voice_audio_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS clip_prompt TEXT,
    ADD COLUMN IF NOT EXISTS clip_status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS clip_retry_count INT DEFAULT 0;

-- ============================================================
-- 2. Add composition_type to composition
-- ============================================================
ALTER TABLE composition
    ADD COLUMN IF NOT EXISTS composition_type VARCHAR(30) DEFAULT 'LEGACY';

-- ============================================================
-- 3. Add frame tracking to composition_task
-- ============================================================
ALTER TABLE composition_task
    ADD COLUMN IF NOT EXISTS current_frame INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_frames INT DEFAULT 0;

-- ============================================================
-- 4. Widen workflow_log.step for new enum values
-- ============================================================
ALTER TABLE workflow_log
    ALTER COLUMN step TYPE VARCHAR(40);

-- ============================================================
-- 5. New table: clip_task (per-frame AI video generation tracking)
-- ============================================================
CREATE TABLE IF NOT EXISTS clip_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storyboard_frame_id UUID NOT NULL REFERENCES storyboard_frame(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    frame_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROMPT_READY','GENERATING','COMPLETED','APPROVED','FAILED','SKIPPED')),
    clip_prompt TEXT,
    reference_image_url TEXT,
    expression_image_url TEXT,
    background_image_url TEXT,
    video_url VARCHAR(500),
    error_message TEXT,
    model_params JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_clip_task_status ON clip_task(status);
CREATE INDEX IF NOT EXISTS idx_clip_task_frame ON clip_task(storyboard_frame_id);
CREATE INDEX IF NOT EXISTS idx_clip_task_project ON clip_task(project_id);
CREATE INDEX IF NOT EXISTS idx_clip_task_project_frame ON clip_task(project_id, frame_number);

-- ============================================================
-- 6. New table: voice_config (per-character TTS settings)
-- ============================================================
CREATE TABLE IF NOT EXISTS voice_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    character_id UUID REFERENCES character(id) ON DELETE SET NULL,
    voice_name VARCHAR(100) NOT NULL DEFAULT 'zh-CN-XiaoxiaoNeural',
    speed DOUBLE PRECISION DEFAULT 1.0,
    pitch INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_voice_config_project ON voice_config(project_id);

-- ============================================================
-- 7. Migrate existing data: insert PENDING logs for 3 new steps
-- ============================================================
DO $$
BEGIN
    -- Insert VOICE_GENERATION for projects that don't have it yet
    INSERT INTO workflow_log (id, project_id, step, status, created_at)
    SELECT gen_random_uuid(), p.id, 'VOICE_GENERATION', 'PENDING', NOW()
    FROM project p
    WHERE NOT EXISTS (
        SELECT 1 FROM workflow_log wl
        WHERE wl.project_id = p.id AND wl.step = 'VOICE_GENERATION'
    );

    -- Insert CLIP_GENERATION
    INSERT INTO workflow_log (id, project_id, step, status, created_at)
    SELECT gen_random_uuid(), p.id, 'CLIP_GENERATION', 'PENDING', NOW()
    FROM project p
    WHERE NOT EXISTS (
        SELECT 1 FROM workflow_log wl
        WHERE wl.project_id = p.id AND wl.step = 'CLIP_GENERATION'
    );

    -- Insert FINAL_COMPOSITION
    INSERT INTO workflow_log (id, project_id, step, status, created_at)
    SELECT gen_random_uuid(), p.id, 'FINAL_COMPOSITION', 'PENDING', NOW()
    FROM project p
    WHERE NOT EXISTS (
        SELECT 1 FROM workflow_log wl
        WHERE wl.project_id = p.id AND wl.step = 'FINAL_COMPOSITION'
    );

    -- For projects where old VIDEO_COMPOSITION was COMPLETED,
    -- mark the three replacement steps as COMPLETED too
    UPDATE workflow_log
    SET status = 'COMPLETED'
    WHERE status = 'PENDING'
    AND step IN ('VOICE_GENERATION', 'CLIP_GENERATION', 'FINAL_COMPOSITION')
    AND project_id IN (
        SELECT project_id FROM workflow_log
        WHERE step = 'VIDEO_COMPOSITION' AND status = 'COMPLETED'
    );
END $$;
