-- An ordinary OLTP table (no partitions). Registered MIRRORED: the heap keeps
-- the full copy and CDC trails every change into an Iceberg mirror.
CREATE TABLE public.vehicles (
    id        bigint PRIMARY KEY,
    vin       text   NOT NULL,
    status    text,
    last_seen bigint NOT NULL
);

INSERT INTO public.vehicles VALUES
    (1, 'VIN-001', 'active', 100),
    (2, 'VIN-002', 'idle',   150),
    (3, 'VIN-003', 'active', 200);
