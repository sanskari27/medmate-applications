ALTER TABLE app_section RENAME COLUMN category TO title;

ALTER TABLE app_section
    ADD COLUMN banner_key TEXT,
    ADD COLUMN banner_content_type TEXT;

UPDATE app_section
SET banner_key = image_key,
    banner_content_type = image_content_type;

ALTER TABLE app_section
    ALTER COLUMN banner_key SET NOT NULL,
    ALTER COLUMN banner_content_type SET NOT NULL;

DROP INDEX app_section_category_key;
DROP INDEX app_section_order;

CREATE UNIQUE INDEX app_section_title_key ON app_section (lower(title));
CREATE INDEX app_section_order ON app_section (position, lower(title));

CREATE TABLE app_section_category (
    id UUID PRIMARY KEY,
    section_id UUID NOT NULL REFERENCES app_section (id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT app_section_category_position CHECK (position >= 0)
);

CREATE UNIQUE INDEX app_section_category_name ON app_section_category (lower(category));
CREATE INDEX app_section_category_order ON app_section_category (section_id, position, lower(category));

CREATE TABLE app_section_category_image (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES app_section_category (id) ON DELETE CASCADE,
    image_key TEXT NOT NULL,
    image_content_type TEXT NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT app_section_category_image_position CHECK (position >= 0)
);

CREATE INDEX app_section_category_image_order ON app_section_category_image (category_id, position, id);

INSERT INTO app_section_category (id, section_id, category, position, created_at, updated_at)
SELECT gen_random_uuid(), id, title, 0, created_at, updated_at
FROM app_section;

INSERT INTO app_section_category_image (
    id, category_id, image_key, image_content_type, position, created_at
)
SELECT gen_random_uuid(), category.id, section.image_key, section.image_content_type, 0, section.created_at
FROM app_section_category category
JOIN app_section section ON section.id = category.section_id;
