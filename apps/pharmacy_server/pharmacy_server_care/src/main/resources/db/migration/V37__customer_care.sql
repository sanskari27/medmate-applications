CREATE TABLE customer_family_member (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    relation TEXT NOT NULL,
    age_years INTEGER,
    birthday DATE,
    conditions TEXT NOT NULL DEFAULT '',
    allergies TEXT NOT NULL DEFAULT '',
    mobile TEXT,
    consent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_family_relation CHECK (relation IN (
        'self', 'mother', 'father', 'spouse', 'son', 'daughter',
        'brother', 'sister', 'grandmother', 'grandfather', 'other')),
    CONSTRAINT customer_family_name_len CHECK (char_length(display_name) BETWEEN 1 AND 80),
    CONSTRAINT customer_family_age CHECK (age_years IS NULL OR (age_years >= 0 AND age_years <= 120)),
    CONSTRAINT customer_family_one_age CHECK (age_years IS NULL OR birthday IS NULL),
    CONSTRAINT customer_family_known_age CHECK (
        relation = 'self' OR age_years IS NOT NULL OR birthday IS NOT NULL),
    CONSTRAINT customer_family_mobile CHECK (mobile IS NULL OR mobile ~ '^\+91[6-9][0-9]{9}$'),
    CONSTRAINT customer_family_consent CHECK (relation = 'self' OR consent)
);

CREATE UNIQUE INDEX customer_family_one_self
    ON customer_family_member (account_id) WHERE relation = 'self';

CREATE INDEX customer_family_member_account
    ON customer_family_member (account_id, display_name, id);

CREATE TABLE customer_prescription (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    source TEXT NOT NULL,
    issued_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_prescription_source_len CHECK (char_length(source) BETWEEN 1 AND 80)
);

CREATE INDEX customer_prescription_account
    ON customer_prescription (account_id, issued_on DESC, created_at DESC);

CREATE TABLE customer_prescription_line (
    id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES customer_prescription (id) ON DELETE CASCADE,
    product_id TEXT,
    name TEXT NOT NULL,
    dose_label TEXT NOT NULL,
    scheduled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT customer_prescription_line_name_len CHECK (char_length(name) BETWEEN 1 AND 120)
);

CREATE INDEX customer_prescription_line_prescription
    ON customer_prescription_line (prescription_id, name, id);

CREATE TABLE customer_medicine (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES customer_family_member (id) ON DELETE CASCADE,
    product_id TEXT,
    name TEXT NOT NULL,
    dose_amount NUMERIC(8, 2) NOT NULL,
    dose_unit TEXT NOT NULL,
    food TEXT NOT NULL,
    weekdays TEXT NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE,
    stock_units NUMERIC(8, 2) NOT NULL,
    units_per_dose NUMERIC(8, 2) NOT NULL,
    reminders BOOLEAN NOT NULL DEFAULT TRUE,
    note TEXT,
    prescription_line_id UUID REFERENCES customer_prescription_line (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_medicine_name_len CHECK (char_length(name) BETWEEN 1 AND 120),
    CONSTRAINT customer_medicine_unit CHECK (dose_unit IN ('tablet', 'capsule', 'ml')),
    CONSTRAINT customer_medicine_food CHECK (food IN ('before', 'after', 'any')),
    CONSTRAINT customer_medicine_days CHECK (weekdays ~ '^[01]{7}$' AND weekdays <> '0000000'),
    CONSTRAINT customer_medicine_span CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT customer_medicine_amount CHECK (dose_amount > 0 AND units_per_dose > 0),
    CONSTRAINT customer_medicine_stock CHECK (stock_units >= 0),
    CONSTRAINT customer_medicine_note_len CHECK (note IS NULL OR char_length(note) <= 120)
);

CREATE INDEX customer_medicine_account
    ON customer_medicine (account_id, member_id, name, id);

CREATE TABLE customer_medicine_time (
    medicine_id UUID NOT NULL REFERENCES customer_medicine (id) ON DELETE CASCADE,
    local_time TIME NOT NULL,
    PRIMARY KEY (medicine_id, local_time)
);

CREATE TABLE customer_dose (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    medicine_id UUID NOT NULL REFERENCES customer_medicine (id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES customer_family_member (id) ON DELETE CASCADE,
    scheduled_on DATE NOT NULL,
    scheduled_at TIME NOT NULL,
    status TEXT NOT NULL,
    acted_at TIMESTAMPTZ,
    CONSTRAINT customer_dose_status CHECK (status IN ('pending', 'taken', 'skipped')),
    CONSTRAINT customer_dose_once UNIQUE (medicine_id, scheduled_on, scheduled_at)
);

CREATE INDEX customer_dose_day
    ON customer_dose (account_id, scheduled_on, member_id);
