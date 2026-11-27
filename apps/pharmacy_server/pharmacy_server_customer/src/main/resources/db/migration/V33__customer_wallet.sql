CREATE TABLE customer_wallet (
    customer_id UUID PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    balance INTEGER NOT NULL,
    referral_code TEXT NOT NULL,
    referred_by UUID REFERENCES account (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_wallet_balance CHECK (balance >= 0),
    CONSTRAINT customer_wallet_code UNIQUE (referral_code),
    CONSTRAINT customer_wallet_code_shape CHECK (referral_code ~ '^[ABCDEFGHJKMNPQRSTVWXYZ23456789]{8}$'),
    CONSTRAINT customer_wallet_no_self CHECK (referred_by IS NULL OR referred_by <> customer_id)
);

CREATE TABLE wallet_entry (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer_wallet (customer_id) ON DELETE CASCADE,
    amount INTEGER NOT NULL,
    kind TEXT NOT NULL,
    order_id UUID,
    related_customer_id UUID REFERENCES account (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT wallet_entry_amount CHECK (amount <> 0),
    CONSTRAINT wallet_entry_kind CHECK (kind IN ('order', 'referral', 'payment')),
    CONSTRAINT wallet_entry_shape CHECK (
        (kind = 'order' AND amount = 15 AND order_id IS NOT NULL AND related_customer_id IS NULL)
        OR (kind = 'referral' AND amount = 100 AND order_id IS NULL AND related_customer_id IS NOT NULL)
        OR (kind = 'payment' AND amount < 0 AND order_id IS NOT NULL AND related_customer_id IS NULL)
    )
);

CREATE UNIQUE INDEX wallet_entry_order_once
    ON wallet_entry (customer_id, order_id) WHERE kind = 'order';

CREATE UNIQUE INDEX wallet_entry_payment_once
    ON wallet_entry (customer_id, order_id) WHERE kind = 'payment';

CREATE UNIQUE INDEX wallet_entry_referral_once
    ON wallet_entry (related_customer_id) WHERE kind = 'referral';

CREATE INDEX wallet_entry_customer
    ON wallet_entry (customer_id, created_at DESC, id DESC);
