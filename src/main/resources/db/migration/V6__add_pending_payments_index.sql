-- Payments stranded at PENDING are found by the reconciliation sweeper.
-- Partial: only PENDING rows are indexed, so this stays tiny no matter how
-- many payments the table holds. Postgres uses it only when the query's
-- WHERE implies this one — a query without status = 'PENDING' will not match.
CREATE INDEX idx_payments_pending_created_at
    ON payments (created_at)
    WHERE status = 'PENDING';