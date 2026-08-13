-- Day 12a: indexes for the two hottest lookups in the app.
-- The pg_dump of the existing schema contained zero CREATE INDEX
-- statements, so both of these queries were doing sequential scans.

-- Supports fetching a merchant's own payments (GET /payments).
CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);

-- Supports fetching the ledger entries belonging to one payment.
CREATE INDEX idx_ledger_entries_payment_id ON ledger_entries(payment_id);