CREATE TABLE idempotency_keys (
                                  id                varchar(255)                NOT NULL,
                                  merchant_id       varchar(255)                NOT NULL,
                                  idempotency_key   varchar(255)                NOT NULL,
                                  payment_id        varchar(255)                NOT NULL,
                                  created_at        timestamp(6) with time zone NOT NULL,

                                  CONSTRAINT idempotency_keys_pkey
                                      PRIMARY KEY (id),

                                  CONSTRAINT idempotency_keys_merchant_key_unique
                                      UNIQUE (merchant_id, idempotency_key),

                                  CONSTRAINT idempotency_keys_payment_fk
                                      FOREIGN KEY (payment_id) REFERENCES payments (id)
);