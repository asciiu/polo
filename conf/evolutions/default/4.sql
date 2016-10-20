#
# --- !Ups

ALTER TABLE poloniex_sessions ALTER COLUMN ended_at DROP NOT NULL;
ALTER TABLE poloniex_sessions ALTER COLUMN ended_at DROP DEFAULT;

ALTER SEQUENCE poloniex_messages_new_id_seq RENAME TO poloniex_messages_id_seq;
ALTER SEQUENCE poloniex_candles_new_id_seq RENAME TO poloniex_candles_id_seq;

ALTER INDEX IF EXISTS poloniex_messages_new_pkey rename TO poloniex_messages_pkey;
ALTER INDEX IF EXISTS poloniex_candles_new_pkey rename TO poloniex_candles_pkey;

ALTER TABLE poloniex_messages RENAME CONSTRAINT "poloniex_messages_new_session_id_fkey" TO "poloniex_messages_session_id_fkey";
ALTER TABLE poloniex_candles RENAME CONSTRAINT "poloniex_candles_new_session_id_fkey" TO "poloniex_candles_session_id_fkey";

# --- !Downs

ALTER SEQUENCE poloniex_messages_id_seq RENAME TO poloniex_messages_new_id_seq;
ALTER SEQUENCE poloniex_candles_id_seq RENAME TO poloniex_candles_new_id_seq;
ALTER TABLE poloniex_messages RENAME CONSTRAINT "poloniex_messages_session_id_fkey" TO "poloniex_messages_new_session_id_fkey";
ALTER TABLE poloniex_candles RENAME CONSTRAINT "poloniex_candles_session_id_fkey" TO "poloniex_candles_new_session_id_fkey";


