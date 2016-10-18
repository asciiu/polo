#
# --- !Ups

ALTER TABLE poloniex_messages ALTER COLUMN created_at TYPE timestamp with time zone;
ALTER TABLE poloniex_messages ALTER COLUMN updated_at TYPE timestamp with time zone;
ALTER TABLE poloniex_candles ALTER COLUMN created_at TYPE timestamp with time zone;



DROP TABLE IF EXISTS poloniex_messages_new;
DROP TABLE IF EXISTS poloniex_candles_new;
DROP TABLE IF EXISTS poloniex_sessions;

CREATE TABLE poloniex_sessions (
    id serial PRIMARY KEY,
    notes text,
    started_at timestamp with time zone not null default now(),
    ended_at timestamp with time zone not null default now()
);


CREATE TABLE poloniex_messages_new (
    id serial PRIMARY KEY,
    session_id integer REFERENCES poloniex_sessions(id) not null,
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
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

CREATE TABLE poloniex_candles_new (
    id serial PRIMARY KEY,
    session_id integer REFERENCES poloniex_sessions(id) not null,
    crypto_currency text NOT NULL,
    open NUMERIC(12, 8) NOT NULL,
    close NUMERIC(12, 8) NOT NULL,
    lowest_ask NUMERIC(12, 8) NOT NULL,
    highest_bid NUMERIC(12, 8) NOT NULL,
    created_at timestamp with time zone not null default now()
);

INSERT INTO poloniex_sessions (id, notes, started_at, ended_at) values (1, 'Test Data 1', '2016-10-12 04:27:51.015-06', '2016-10-12 15:03:55.573-06');

INSERT INTO poloniex_messages_new SELECT id, 1, crypto_currency, last, lowest_ask, highest_bid, percent_change, base_volume, quote_volume, is_frozen, high_24hr, low_24hr, created_at, updated_at FROM poloniex_messages;
DROP TABLE poloniex_messages;
ALTER TABLE poloniex_messages_new RENAME TO poloniex_messages;

INSERT INTO poloniex_candles_new SELECT id, 1, crypto_currency, open, close, lowest_ask, highest_bid, created_at FROM poloniex_candles;
DROP TABLE poloniex_candles;
ALTER TABLE poloniex_candles_new RENAME TO poloniex_candles;

# --- !Downs

DROP TABLE IF EXISTS poloniex_messages_new;
DROP TABLE IF EXISTS poloniex_candles_new;
DROP TABLE IF EXISTS poloniex_sessions;

