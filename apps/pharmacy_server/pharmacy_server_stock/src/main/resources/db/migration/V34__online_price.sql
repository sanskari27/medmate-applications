ALTER TABLE inventory_product
    ADD COLUMN online_mrp NUMERIC(12, 2),
    ADD COLUMN online_price NUMERIC(12, 2),
    ADD CONSTRAINT inventory_online_price CHECK (
        (online_mrp IS NULL AND online_price IS NULL)
        OR (online_mrp >= 0 AND online_price >= 0 AND online_price <= online_mrp)
    );
