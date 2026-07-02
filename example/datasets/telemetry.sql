-- A bulkier table for the lifecycle walkthrough: its initial copy is killed
-- mid-flight and resumed from the journal.
CREATE TABLE public.telemetry (
    id      bigint PRIMARY KEY,
    payload text   NOT NULL,
    ts      bigint NOT NULL
);

INSERT INTO public.telemetry
SELECT g, 'payload-' || g, g FROM generate_series(1, 20000) g;
