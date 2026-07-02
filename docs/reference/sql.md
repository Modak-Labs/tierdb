# SQL API

Everything the `modak` extension exposes in SQL: five functions and four
session GUCs.

## Functions

### `modak_upsert(table regclass, row jsonb) → text`

Routes one record by its tier key vs the cut-line. Recent rows become plain
heap DML, cold rows become a `modak.delta` upsert entry. `row` is the full row
image, keys matching column names. Returns the route taken.

```sql
SELECT modak_upsert('public.events'::regclass,
                    '{"id":3,"event_time":110,"val":"C!"}'::jsonb);
```

### `modak_delete(table regclass, key jsonb, tier_key bigint) → text`

Routes one delete. `key` is the primary key, a bare value for a single-column
key or an object for composite keys. Cold deletes become delta tombstones.
The key fields are kept as the payload because the compaction fold needs
their typed values.

```sql
SELECT modak_delete('public.events'::regclass, '1', 10);
SELECT modak_delete('public.fleet'::regclass, '{"tenant_id":2,"vehicle_id":7}', 90);
```

### `modak_read_begin(table regclass) → (pin_id bigint, ...)`

Pins the table's current `(T, S)` for this transaction and returns the pin.
The pin is a row in `modak.read_pins` and rolls back with the transaction, so
a crashed client cannot leak one.

### `modak_rewrite_scan(table regclass) → text`

Renders the exact two-tier union SQL for the pinned view, the same query the
transparent-read hook substitutes. Run it inline (`FROM ( :scan_sql ) q`) or
inspect it.

### `modak_read_end(pin_id bigint)`

Releases a pin before commit. Optional, since commit and abort clean up
regardless, but polite for long transactions.

### `modak_version() → text`

The installed extension version.

## Session GUCs

| GUC | Default | Meaning |
|-----|---------|---------|
| `modak.transparent_reads` | `on` | Rewrite `SELECT`s on registered tables into the two-tier union scan, pinning `(T, S)` for the transaction |
| `modak.mirrored_reads` | `'heap'` | Read mode for mirrored tables without retention. `'heap'` leaves plain scans untouched, `'hybrid'` serves the bulk from the lake |
| `modak.mirror_wait_ms` | `5000` | Bounded wait (ms) for the mirror frontier before a hybrid read. On timeout the query falls back to the heap with a `NOTICE` |
| `modak.hybrid_lag` | `0` | Hybrid seam margin in tier-key units. The union splits at `max(tier_key) - lag` |

## Views

`modak.status` has one row per registered table: mode, cut-line `T` and pinned
snapshot `S`, mirror frontier, delta backlog, active read pins, whether an
initial copy is in flight, and partition state counts. See the
[catalog reference](catalog.md).
