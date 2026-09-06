-- Where a merchant wants payment.settled webhooks delivered.
-- Nullable: a merchant without one simply isn't called.
ALTER TABLE merchants ADD COLUMN webhook_url VARCHAR(512);