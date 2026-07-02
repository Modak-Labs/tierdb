#!/usr/bin/env bash
# Corrections to cold rows: routed via the delta, folded by compaction,
# then one pinned read spanning both tiers.
set -euo pipefail
source "$(dirname "$0")/../lib.sh"

say "4. Route corrections: tombstone id=1, correct id=3 (both cold), insert id=6 (hot)"
$PSQL <<'SQL'
SELECT modak_delete('public.events'::regclass, '1', 10);
SELECT modak_upsert('public.events'::regclass, '{"id":3,"event_time":110,"val":"C!"}'::jsonb);
SELECT modak_upsert('public.events'::regclass, '{"id":6,"event_time":260,"val":"f"}'::jsonb);
SQL
$PSQL -c "SELECT pk, op, tier_key FROM modak.delta ORDER BY pk"

say "5. Compaction folds the delta into Iceberg (equality deletes)"
wait_for "delta folded and cleared" "SELECT count(*) FROM modak.delta" "0"

EXPECTED="2|20|b
3|110|C!
4|150|d
5|250|e
6|260|f"

say "6. One pinned read spanning both tiers (explicit protocol)"
RESULT=$($PSQL -tA <<'SQL'
BEGIN;
SET LOCAL duckdb.max_workers_per_postgres_scan = 0;
SELECT pin_id AS pin FROM modak_read_begin('public.events'::regclass) \gset
SELECT modak_rewrite_scan('public.events'::regclass) AS scan_sql \gset
SELECT string_agg(id::text || '|' || event_time::text || '|' || coalesce(val,''), E'\n' ORDER BY id)
FROM ( :scan_sql ) q;
SELECT modak_read_end(:pin) \gset _
COMMIT;
SQL
)
echo "$RESULT"
assert_eq "pinned read (explicit protocol)" "$EXPECTED" "$RESULT"

say "6b. The same read as ONE plain SQL statement (planner hook)"
RESULT_TRANSPARENT=$($PSQL -tA -c \
    "SELECT string_agg(id::text || '|' || event_time::text || '|' || coalesce(val,''), E'\n' ORDER BY id) FROM public.events")
echo "$RESULT_TRANSPARENT"
assert_eq "transparent read" "$EXPECTED" "$RESULT_TRANSPARENT"
