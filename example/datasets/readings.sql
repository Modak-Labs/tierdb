-- A timestamptz time series with daily partitions. The tier key is the
-- timestamp itself: no epoch columns, the codec handles the axis.
CREATE TABLE public.readings (
    id      bigint NOT NULL,
    ts      timestamptz NOT NULL,
    celsius double precision
) PARTITION BY RANGE (ts);

CREATE TABLE public.readings_d1 PARTITION OF public.readings
    FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2026-01-02 00:00:00+00');
CREATE TABLE public.readings_d2 PARTITION OF public.readings
    FOR VALUES FROM ('2026-01-02 00:00:00+00') TO ('2026-01-03 00:00:00+00');
CREATE TABLE public.readings_d3 PARTITION OF public.readings
    FOR VALUES FROM ('2026-01-03 00:00:00+00') TO ('2026-01-04 00:00:00+00');

INSERT INTO public.readings VALUES
    (1, '2026-01-01 08:00:00+00', 19.5),
    (2, '2026-01-01 20:00:00+00', 18.1),
    (3, '2026-01-02 08:00:00+00', 21.0),
    (4, '2026-01-02 20:00:00+00', 20.4),
    (5, '2026-01-03 08:00:00+00', 22.3);
