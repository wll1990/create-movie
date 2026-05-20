-- V8: Fix column length overflow issues
ALTER TABLE character ALTER COLUMN gender TYPE VARCHAR(50);
ALTER TABLE character ALTER COLUMN age_range TYPE VARCHAR(50);
ALTER TABLE character ALTER COLUMN image_generation_status TYPE VARCHAR(30);
