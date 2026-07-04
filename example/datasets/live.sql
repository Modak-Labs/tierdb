-- Two live time-series tables for the streaming demo (live.sh). Same shape as
-- events.sql but at real-world scale: the tier key is epoch seconds, hourly
-- partitions, and 24 hours of seeded history for the worker to tier away.
CREATE TABLE public.sensor_readings (
    id      bigint NOT NULL,
    ts      bigint NOT NULL,
    device  text   NOT NULL,
    reading double precision NOT NULL
) PARTITION BY RANGE (ts);

CREATE TABLE public.trades (
    id     bigint NOT NULL,
    ts     bigint NOT NULL,
    symbol text   NOT NULL,
    px     numeric(12,4) NOT NULL,
    qty    int    NOT NULL
) PARTITION BY RANGE (ts);

DO $$
DECLARE
    now_s bigint := extract(epoch FROM now())::bigint;
    lo    bigint := (now_s - 24 * 3600) / 3600 * 3600;
BEGIN
    WHILE lo < now_s + 2 * 3600 LOOP
        EXECUTE format('CREATE TABLE public.sensor_readings_%s PARTITION OF public.sensor_readings
                        FOR VALUES FROM (%s) TO (%s)', lo, lo, lo + 3600);
        EXECUTE format('CREATE TABLE public.trades_%s PARTITION OF public.trades
                        FOR VALUES FROM (%s) TO (%s)', lo, lo, lo + 3600);
        lo := lo + 3600;
    END LOOP;
END $$;

INSERT INTO public.sensor_readings
SELECT t * 10, t, 'device-' || (t % 7), 20 + 10 * sin(t / 3600.0) + random()
FROM generate_series(
    (extract(epoch FROM now())::bigint - 24 * 3600) / 3600 * 3600,
    extract(epoch FROM now())::bigint - 1, 2) t;

INSERT INTO public.trades
SELECT t * 10, t, (ARRAY['MODK','ICEB','DUCK','PGRS'])[1 + (t % 4)::int],
       100 + 20 * sin(t / 7200.0) + random(), 1 + (t % 50)::int
FROM generate_series(
    (extract(epoch FROM now())::bigint - 24 * 3600) / 3600 * 3600,
    extract(epoch FROM now())::bigint - 1, 2) t;
