CREATE TABLE pharmacy_hours (
    pharmacy_id UUID NOT NULL REFERENCES pharmacy (id) ON DELETE CASCADE,
    day TEXT NOT NULL,
    closed BOOLEAN NOT NULL,
    opens_at TEXT,
    closes_at TEXT,
    PRIMARY KEY (pharmacy_id, day),
    CONSTRAINT pharmacy_hours_day CHECK (
        day IN ('monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday')
    ),
    CONSTRAINT pharmacy_hours_window CHECK (
        (closed AND opens_at IS NULL AND closes_at IS NULL)
        OR (NOT closed AND opens_at IS NOT NULL AND closes_at IS NOT NULL)
    )
);
