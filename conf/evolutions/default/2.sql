#
# --- !Ups

DROP TABLE IF EXISTS poloniex_messages;
DROP TABLE IF EXISTS poloniex_candles;

CREATE TABLE poloniex_messages (
    id serial PRIMARY KEY,
    crypto_currency text NOT NULL,
    last NUMERIC(12, 8) NOT NULL,
    lowest_ask NUMERIC(12, 8) NOT NULL,
    highest_bid NUMERIC(12, 8) NOT NULL,
    percent_change NUMERIC(12, 8) NOT NULL,
    base_volume NUMERIC(24, 8) NOT NULL,
    quote_volume NUMERIC(24, 8) NOT NULL,
    is_frozen BOOLEAN NOT NULL,
    high_24hr NUMERIC(12, 8) NOT NULL,
    low_24hr NUMERIC(12, 8) NOT NULL,
    created_at timestamp without time zone not null default now(),
    updated_at timestamp without time zone not null default now()
);


CREATE TABLE poloniex_candles (
    id serial PRIMARY KEY,
    crypto_currency text NOT NULL,
    open NUMERIC(12, 8) NOT NULL,
    close NUMERIC(12, 8) NOT NULL,
    lowest_ask NUMERIC(12, 8) NOT NULL,
    highest_bid NUMERIC(12, 8) NOT NULL,
    created_at timestamp without time zone not null default now()
);


# --- !Downs

DROP TABLE poloniex_messages;
DROP TABLE poloniex_candles;
