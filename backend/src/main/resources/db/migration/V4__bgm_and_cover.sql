-- Flyway Migration V4: BGM, Cover URL, Episode Summary
-- Adds bgm_material_id to composition, cover_url, and episode summary field.

-- Add BGM material reference to composition
ALTER TABLE composition ADD COLUMN IF NOT EXISTS bgm_material_id UUID;

-- Add episode summary for cross-episode script continuity
ALTER TABLE episode ADD COLUMN IF NOT EXISTS summary TEXT;

-- Note: composition.cover_url already exists from V1 migration
