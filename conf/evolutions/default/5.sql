#
# --- !Ups

DROP TYPE IF EXISTS order_type;

CREATE TYPE order_type AS ENUM (
    'buy',
    'sell'
);

CREATE TABLE poloniex_orders (
    id serial PRIMARY KEY,
    crypto_currency text NOT NULL,
    price NUMERIC(12, 8) NOT NULL,
    quantity NUMERIC(12, 8) NOT NULL,
    btc_total NUMERIC(12, 8) NOT NULL,
    order_type order_type NOT NULL,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

# --- !Downs
DROP TYPE IF EXISTS order_side;
DROP TABLE IF EXISTS poloniex_orders;

