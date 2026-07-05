#!/usr/bin/env bash
# A TIERED table keyed on timestamptz: the codec, day layout, and native SQL.
set -euo pipefail
source "$(dirname "$0")/../lib.sh"

say "1. Create a daily-partitioned timestamptz table and seed it (datasets/readings.sql)"
$PSQL < example/datasets/readings.sql
echo "   5 rows across 3 daily partitions; high-water ts = 2026-01-03 08:00+00"

say "2. Register with the timestamp column as the tier key (type is detected)"
docker compose run --rm worker register --table public.readings --pk id --tier-key ts

say "3. Worker tiers the days behind the high-water mark"
wait_for "cut-line advanced to 2026-01-03 (d1+d2 tiered)" \
    "SELECT tier_key_hi = (extract(epoch FROM timestamptz '2026-01-03 00:00:00+00') * 1000000)::bigint \
     FROM modak.cutline WHERE table_id = 'public.readings'::regclass::oid::bigint" \
    "t"
wait_for "tiered partitions physically dropped" \
    "SELECT count(*) FROM pg_inherits JOIN pg_class c ON c.oid = inhrelid \
     WHERE inhparent = 'public.readings'::regclass \
     AND c.relname IN ('readings_d1', 'readings_d2')" \
    "0"

echo "   plain SELECT with a native timestamptz predicate spans both tiers:"
$PSQL -c "SELECT * FROM public.readings WHERE ts < '2026-01-03 00:00:00+00' ORDER BY id"

assert_eq "transparent read sees all rows" "5" \
    "$($PSQL -tA -c 'SELECT count(*) FROM public.readings')"
assert_eq "native predicate on cold rows answers from the lake" "4" \
    "$($PSQL -tA -c "SELECT count(*) FROM public.readings WHERE ts < '2026-01-03 00:00:00+00'")"
assert_eq "raw heap holds only the hot day" "1" \
    "$($PSQL -tA -c 'SET modak.transparent_reads = off; SELECT count(*) FROM public.readings')"

say "4. A native-typed delete routes by the encoded key"
routed=$($PSQL -tA -c "SELECT modak_delete('public.readings'::regclass, '1', TIMESTAMPTZ '2026-01-01 08:00:00+00')")
echo "   modak_delete routed to: $routed"
assert_eq "cold delete became a delta tombstone" "delta" "$routed"
assert_eq "transparent read hides the tombstoned row" "4" \
    "$($PSQL -tA -c 'SELECT count(*) FROM public.readings')"
