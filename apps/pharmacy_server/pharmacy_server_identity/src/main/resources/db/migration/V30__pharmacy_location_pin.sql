ALTER TABLE pharmacy
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE pharmacy
    ADD CONSTRAINT pharmacy_pin CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    ),
    ADD CONSTRAINT pharmacy_latitude CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    ADD CONSTRAINT pharmacy_longitude CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180));
