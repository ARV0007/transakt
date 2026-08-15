-- Day 12c step 1 (expand): add hashed API key storage alongside the plaintext
-- column. Nothing is dropped and nothing is made NOT NULL — ApiKeyFilter still
-- reads api_key, and Merchant.java doesn't map these columns yet. Both the
-- NOT NULL constraints and the drop of api_key belong to the contract phase,
-- once every writer populates the new columns.

ALTER TABLE merchants ADD COLUMN api_key_prefix varchar(16);
ALTER TABLE merchants ADD COLUMN api_key_hash varchar(64);

-- Backfill from the existing plaintext keys. This is the ONLY moment this is
-- possible: once api_key is dropped, the originals are gone for good.
UPDATE merchants
SET api_key_prefix = left(api_key, 8),
    api_key_hash   = encode(sha256(convert_to(api_key, 'UTF8')), 'hex');

CREATE UNIQUE INDEX idx_merchants_api_key_prefix ON merchants(api_key_prefix);