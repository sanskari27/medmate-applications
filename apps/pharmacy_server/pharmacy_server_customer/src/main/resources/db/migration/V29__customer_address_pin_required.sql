DELETE FROM customer_delivery_address
WHERE latitude IS NULL OR longitude IS NULL;

ALTER TABLE customer_delivery_address
    ALTER COLUMN latitude SET NOT NULL,
    ALTER COLUMN longitude SET NOT NULL;

ALTER TABLE customer_delivery_address
    DROP CONSTRAINT customer_address_pin;

ALTER TABLE customer_delivery_address
    ADD CONSTRAINT customer_address_latitude CHECK (latitude >= -90 AND latitude <= 90),
    ADD CONSTRAINT customer_address_longitude CHECK (longitude >= -180 AND longitude <= 180);
