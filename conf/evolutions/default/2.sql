#
# --- !Ups

DROP TABLE IF EXISTS poloniex_messages;

CREATE TABLE poloniex_messages (
    id serial PRIMARY KEY,
    crypto_currency text NOT NULL,
    last NUMERIC(12, 8) NOT NULL,
    lowest_ask NUMERIC(12, 8) NOT NULL,
    highest_bid NUMERIC(12, 8) NOT NULL,
    percent_change NUMERIC(12, 8) NOT NULL,
    base_volume NUMERIC(12, 8) NOT NULL,
    quote_volume NUMERIC(12, 8) NOT NULL,
    is_frozen BOOLEAN NOT NULL,
    high_24hr NUMERIC(12, 8) NOT NULL,
    low_24hr NUMERIC(12, 8) NOT NULL,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);


# --- !Downs

DROP TABLE poloniex_messages;
