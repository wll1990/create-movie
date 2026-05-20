ALTER TABLE "character" ADD COLUMN IF NOT EXISTS candidate_portraits jsonb DEFAULT '[]';
ALTER TABLE "character" ADD COLUMN IF NOT EXISTS selected_portrait_index integer;
ALTER TABLE "character" ADD COLUMN IF NOT EXISTS image_generation_status varchar(20) DEFAULT 'PENDING';
