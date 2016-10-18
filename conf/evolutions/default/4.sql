#
# --- !Ups

ALTER TABLE poloniex_sessions ALTER COLUMN ended_at DROP NOT NULL;
ALTER TABLE poloniex_sessions ALTER COLUMN ended_at DROP DEFAULT;

# --- !Downs



