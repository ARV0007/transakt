-- Indexes for the retention sweeper. Neither existing index helps it.
--
-- V7's outbox index is PARTIAL on published_at IS NULL. That is the publisher's
-- query and the exact mirror of the sweeper's. A partial index pays off when its
-- predicate selects a MINORITY of rows: unpublished events are a transient few, so
-- that index stays the size of the backlog. Published events are nearly the whole
-- table, so the mirror-image partial index would be no smaller than a plain one.
CREATE INDEX idx_outbox_events_published_at
    ON outbox_events (published_at);

-- idempotency_keys had no index on created_at at all. The unique constraint covers
-- (merchant_id, idempotency_key), which a range scan on age cannot use.
CREATE INDEX idx_idempotency_keys_created_at
    ON idempotency_keys (created_at);
