-- Ordered category labels for a drug, matching otc_fmcg_categories.

CREATE TABLE IF NOT EXISTS drug_categories (
  product_id text NOT NULL REFERENCES drugs (product_id),
  position integer NOT NULL,
  segment text NOT NULL,
  PRIMARY KEY (product_id, position)
);
