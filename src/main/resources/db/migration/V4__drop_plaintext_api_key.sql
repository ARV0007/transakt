-- Day 12c step 4 (contract): ApiKeyFilter reads api_key_prefix + api_key_hash and
-- MerchantService writes them on every signup, so the plaintext column has no
-- remaining reader or writer. Merchant.apiKey is now @Transient, so a new merchant
-- still sees their key once in the creation response.
--
-- IRREVERSIBLE: after this runs, the original API keys exist nowhere.

ALTER TABLE merchants ALTER COLUMN api_key_prefix SET NOT NULL;
ALTER TABLE merchants ALTER COLUMN api_key_hash SET NOT NULL;

ALTER TABLE merchants DROP COLUMN api_key;