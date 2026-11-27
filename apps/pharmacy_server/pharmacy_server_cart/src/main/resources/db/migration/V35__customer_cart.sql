CREATE TABLE customer_cart (
    account_id UUID PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    coupon_code TEXT,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE customer_cart_line (
    account_id UUID NOT NULL REFERENCES customer_cart (account_id) ON DELETE CASCADE,
    product_id TEXT NOT NULL,
    qty INTEGER NOT NULL,
    PRIMARY KEY (account_id, product_id),
    CONSTRAINT customer_cart_line_qty CHECK (qty >= 1 AND qty <= 99)
);

CREATE TABLE customer_cart_pharmacy (
    account_id UUID NOT NULL REFERENCES customer_cart (account_id) ON DELETE CASCADE,
    pharmacy_id UUID NOT NULL REFERENCES pharmacy (id),
    PRIMARY KEY (account_id, pharmacy_id)
);
