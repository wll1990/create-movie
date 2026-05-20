-- Flyway Migration V3: Multi-episode support
-- Adds Episode entity, attaches episode_id to per-episode tables,
-- migrates existing data into Episode 1.

-- ============================================================
-- 1. Create episode table
-- ============================================================
CREATE TABLE IF NOT EXISTS episode (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    episode_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    progress JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(project_id, episode_number)
);

CREATE INDEX IF NOT EXISTS idx_episode_project ON episode(project_id);

-- ============================================================
-- 2. Add episode_id to per-episode tables
-- ============================================================
ALTER TABLE script ADD COLUMN IF NOT EXISTS episode_id UUID;
ALTER TABLE storyboard ADD COLUMN IF NOT EXISTS episode_id UUID;
ALTER TABLE composition ADD COLUMN IF NOT EXISTS episode_id UUID;
ALTER TABLE workflow_log ADD COLUMN IF NOT EXISTS episode_id UUID;
ALTER TABLE clip_task ADD COLUMN IF NOT EXISTS episode_id UUID;

CREATE INDEX IF NOT EXISTS idx_script_episode ON script(episode_id);
CREATE INDEX IF NOT EXISTS idx_storyboard_episode ON storyboard(episode_id);
CREATE INDEX IF NOT EXISTS idx_composition_episode ON composition(episode_id);
CREATE INDEX IF NOT EXISTS idx_wf_log_episode ON workflow_log(episode_id);
CREATE INDEX IF NOT EXISTS idx_clip_task_episode ON clip_task(episode_id);

-- ============================================================
-- 3. Migrate existing data: create Episode 1 for each project
-- ============================================================
DO $$
DECLARE
    r RECORD;
    ep_id UUID;
BEGIN
    FOR r IN SELECT id, title, status FROM project LOOP
        -- Create episode 1 if not exists
        INSERT INTO episode (id, project_id, episode_number, title, status)
        VALUES (gen_random_uuid(), r.id, 1, r.title, r.status)
        ON CONFLICT (project_id, episode_number) DO NOTHING
        RETURNING id INTO ep_id;

        -- If insert did nothing (already exists), fetch the id
        IF ep_id IS NULL THEN
            SELECT id INTO ep_id FROM episode
            WHERE project_id = r.id AND episode_number = 1;
        END IF;

        -- Link existing script
        UPDATE script SET episode_id = ep_id
        WHERE project_id = r.id AND episode_id IS NULL;

        -- Link existing storyboard
        UPDATE storyboard SET episode_id = ep_id
        WHERE project_id = r.id AND episode_id IS NULL;

        -- Link existing composition
        UPDATE composition SET episode_id = ep_id
        WHERE project_id = r.id AND episode_id IS NULL;

        -- Link existing workflow_log
        UPDATE workflow_log SET episode_id = ep_id
        WHERE project_id = r.id AND episode_id IS NULL;

        -- Link existing clip_task
        UPDATE clip_task SET episode_id = ep_id
        WHERE project_id = r.id AND episode_id IS NULL;
    END LOOP;
END $$;
