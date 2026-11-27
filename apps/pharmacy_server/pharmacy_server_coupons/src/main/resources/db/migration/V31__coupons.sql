CREATE TABLE coupon (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL,
    title TEXT NOT NULL,
    discount_type TEXT NOT NULL,
    discount_value NUMERIC(12, 2) NOT NULL,
    max_discount NUMERIC(12, 2),
    min_order NUMERIC(12, 2),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    max_redemptions INTEGER,
    max_per_customer INTEGER,
    pharmacy_scope TEXT NOT NULL,
    active BOOLEAN NOT NULL,
    banner_key TEXT NOT NULL,
    banner_content_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT coupon_code_unique UNIQUE (code),
    CONSTRAINT coupon_type CHECK (discount_type IN ('pct', 'flat')),
    CONSTRAINT coupon_scope CHECK (pharmacy_scope IN ('all', 'selected')),
    CONSTRAINT coupon_value CHECK (discount_value > 0),
    CONSTRAINT coupon_percent CHECK (discount_type <> 'pct' OR discount_value <= 100),
    CONSTRAINT coupon_max CHECK (max_discount IS NULL OR max_discount > 0),
    CONSTRAINT coupon_max_only_pct CHECK (discount_type = 'pct' OR max_discount IS NULL),
    CONSTRAINT coupon_min CHECK (min_order IS NULL OR min_order > 0),
    CONSTRAINT coupon_window CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT coupon_uses CHECK (max_redemptions IS NULL OR max_redemptions > 0),
    CONSTRAINT coupon_per_customer CHECK (max_per_customer IS NULL OR max_per_customer > 0)
);

CREATE TABLE coupon_pharmacy (
    coupon_id UUID NOT NULL REFERENCES coupon (id) ON DELETE CASCADE,
    pharmacy_id UUID NOT NULL REFERENCES pharmacy (id),
    PRIMARY KEY (coupon_id, pharmacy_id)
);

CREATE TABLE coupon_redemption (
    id UUID PRIMARY KEY,
    coupon_id UUID NOT NULL REFERENCES coupon (id),
    customer_id UUID NOT NULL REFERENCES account (id),
    pharmacy_id UUID REFERENCES pharmacy (id),
    order_total NUMERIC(12, 2) NOT NULL,
    discount NUMERIC(12, 2) NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT coupon_redemption_total CHECK (order_total >= 0),
    CONSTRAINT coupon_redemption_discount CHECK (discount >= 0)
);

CREATE INDEX coupon_created ON coupon (created_at DESC, id DESC);
CREATE INDEX coupon_redemption_coupon ON coupon_redemption (coupon_id);
CREATE INDEX coupon_redemption_customer ON coupon_redemption (coupon_id, customer_id);
