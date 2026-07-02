-- A range-partitioned event stream. The partition key (event_time) is the
-- tier key: Modak tiers whole partitions behind the data high-water mark.
CREATE TABLE public.events (
    id         bigint NOT NULL,
    event_time bigint NOT NULL,
    val        text
) PARTITION BY RANGE (event_time);

CREATE TABLE public.events_p0 PARTITION OF public.events FOR VALUES FROM (0)   TO (100);
CREATE TABLE public.events_p1 PARTITION OF public.events FOR VALUES FROM (100) TO (200);
CREATE TABLE public.events_p2 PARTITION OF public.events FOR VALUES FROM (200) TO (300);

INSERT INTO public.events VALUES
    (1, 10,  'a'),
    (2, 20,  'b'),
    (3, 110, 'c'),
    (4, 150, 'd'),
    (5, 250, 'e');
