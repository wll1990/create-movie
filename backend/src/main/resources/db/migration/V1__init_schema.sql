-- Flyway Migration V1: Initial schema
-- Creates all tables for the MakeMovie application

-- Enable pgvector extension (requires pgvector installed for PG version)
-- CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- Project
-- ============================================================
CREATE TABLE project (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    track VARCHAR(50),
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('ANALYSIS','CREATION','HYBRID')),
    status VARCHAR(20) DEFAULT 'DRAFT',
    source_video_gene_id UUID,
    creation_template_id UUID,
    progress JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Script
-- ============================================================
CREATE TABLE script (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    title VARCHAR(200),
    track VARCHAR(50),
    duration INT,
    content JSONB NOT NULL,
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_script_project ON script(project_id);

-- ============================================================
-- Scene
-- ============================================================
CREATE TABLE scene (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    script_id UUID NOT NULL REFERENCES script(id) ON DELETE CASCADE,
    scene_number INT NOT NULL,
    location VARCHAR(200),
    time_of_day VARCHAR(50),
    summary TEXT,
    dialogues JSONB NOT NULL DEFAULT '[]',
    duration_estimate INT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_scene_script ON scene(script_id);

-- ============================================================
-- Character
-- ============================================================
CREATE TABLE character (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(50),
    gender VARCHAR(20),
    age_range VARCHAR(30),
    personality TEXT,
    appearance JSONB DEFAULT '{}',
    expressions JSONB DEFAULT '[]',
    voice_config JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_character_project ON character(project_id);

-- ============================================================
-- Storyboard
-- ============================================================
CREATE TABLE storyboard (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    script_id UUID NOT NULL REFERENCES script(id),
    total_frames INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_storyboard_project ON storyboard(project_id);

-- ============================================================
-- Storyboard Frame
-- ============================================================
CREATE TABLE storyboard_frame (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storyboard_id UUID NOT NULL REFERENCES storyboard(id) ON DELETE CASCADE,
    scene_id UUID NOT NULL REFERENCES scene(id),
    frame_number INT NOT NULL,
    shot_type VARCHAR(50),
    camera_angle VARCHAR(50),
    bg_description TEXT,
    bg_image_url VARCHAR(500),
    characters JSONB NOT NULL DEFAULT '[]',
    dialogue_id UUID,
    subtitle_text TEXT,
    duration_sec DOUBLE PRECISION DEFAULT 3.0,
    transition VARCHAR(50) DEFAULT 'cut',
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_frame_storyboard ON storyboard_frame(storyboard_id);
CREATE INDEX idx_frame_scene ON storyboard_frame(scene_id);

-- ============================================================
-- Material
-- ============================================================
CREATE TABLE material (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    type VARCHAR(50) NOT NULL CHECK (type IN ('IMAGE','AUDIO','VIDEO','FONT','TEMPLATE')),
    category VARCHAR(50),
    name VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    metadata JSONB DEFAULT '{}',
    tags TEXT[] DEFAULT '{}',
    source VARCHAR(50) DEFAULT 'UPLOADED' CHECK (source IN ('UPLOADED','SYSTEM','AI_GENERATED')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_material_type ON material(type);
CREATE INDEX idx_material_category ON material(category);

-- ============================================================
-- Composition
-- ============================================================
CREATE TABLE composition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    storyboard_id UUID NOT NULL REFERENCES storyboard(id),
    video_url VARCHAR(500),
    cover_url VARCHAR(500),
    duration_sec INT,
    resolution VARCHAR(20) DEFAULT '1080x1920',
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    composition_config JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_composition_project ON composition(project_id);

-- ============================================================
-- Composition Task (queue)
-- ============================================================
CREATE TABLE composition_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    composition_id UUID NOT NULL REFERENCES composition(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED')),
    progress INT DEFAULT 0,
    ffmpeg_command TEXT,
    ffmpeg_log TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_task_status ON composition_task(status);
CREATE INDEX idx_task_composition ON composition_task(composition_id);

-- ============================================================
-- Video Gene
-- ============================================================
CREATE TABLE video_gene (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES project(id),
    track VARCHAR(50) NOT NULL,
    content_gene JSONB NOT NULL,
    visual_gene JSONB NOT NULL,
    audio_gene JSONB NOT NULL,
    traffic_gene JSONB NOT NULL,
    -- embedding_vector vector(1536),  -- requires pgvector
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_gene_project ON video_gene(project_id);

-- ============================================================
-- Creation Template
-- ============================================================
CREATE TABLE creation_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_gene_id UUID REFERENCES video_gene(id),
    name VARCHAR(200) NOT NULL,
    narrative_config JSONB NOT NULL,
    visual_config JSONB NOT NULL,
    audio_config JSONB NOT NULL,
    pacing_config JSONB NOT NULL,
    editable BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Workflow Log
-- ============================================================
CREATE TABLE workflow_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    step VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    prompt TEXT,
    input_data JSONB DEFAULT '{}',
    output_data JSONB DEFAULT '{}',
    error_message TEXT,
    llm_response_time_ms INT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_wf_log_project ON workflow_log(project_id);
CREATE INDEX idx_wf_log_step ON workflow_log(project_id, step);

-- ============================================================
-- Create updated_at trigger function
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_project_updated_at
    BEFORE UPDATE ON project
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
