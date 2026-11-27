CREATE TABLE app_section (
    id UUID PRIMARY KEY,
    category TEXT NOT NULL,
    image_key TEXT NOT NULL,
    image_content_type TEXT NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT app_section_position CHECK (position >= 0)
);

CREATE UNIQUE INDEX app_section_category_key ON app_section (lower(category));
CREATE INDEX app_section_order ON app_section (position, lower(category));
