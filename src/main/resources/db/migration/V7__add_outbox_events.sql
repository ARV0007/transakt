-- The outbox. An event row is written in the SAME transaction as the payment it
-- describes, so either both commit or neither does. A separate publisher sweeps
-- rows where published_at IS NULL and sends them to Kafka.
--
-- This exists because a lost event means a merchant is never told their payment
-- settled: they don't ship, the customer has been charged, and nothing in the
-- system looks wrong. Publishing after commit would leave exactly that window.
CREATE TABLE outbox_events (
                               id            VARCHAR(36) PRIMARY KEY,
                               aggregate_id  VARCHAR(36)  NOT NULL,
                               event_type    VARCHAR(64)  NOT NULL,
                               payload       TEXT         NOT NULL,
                               created_at    TIMESTAMPTZ  NOT NULL,
                               published_at  TIMESTAMPTZ
);

-- Partial index, same reasoning as V6: unpublished rows are a transient minority,
-- so indexing only them keeps this the size of the backlog rather than the table.
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;