CREATE TABLE customer_delivery_address (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    line1 TEXT NOT NULL,
    line2 TEXT NOT NULL,
    landmark TEXT,
    city TEXT NOT NULL,
    pincode TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    receiver_name TEXT NOT NULL,
    receiver_phone TEXT NOT NULL,
    call_on_arrival BOOLEAN NOT NULL DEFAULT FALSE,
    leave_with_security BOOLEAN NOT NULL DEFAULT FALSE,
    dont_ring_bell BOOLEAN NOT NULL DEFAULT FALSE,
    lift_not_working BOOLEAN NOT NULL DEFAULT FALSE,
    rider_note TEXT,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_address_label CHECK (label IN ('home', 'work', 'other')),
    CONSTRAINT customer_address_pincode CHECK (pincode ~ '^560[0-9]{3}$'),
    CONSTRAINT customer_address_note_len CHECK (rider_note IS NULL OR char_length(rider_note) <= 140),
    CONSTRAINT customer_address_pin CHECK (
        (latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    )
);

CREATE UNIQUE INDEX customer_delivery_address_one_selected
    ON customer_delivery_address (account_id) WHERE selected;

CREATE INDEX customer_delivery_address_account
    ON customer_delivery_address (account_id, updated_at DESC, id DESC);
