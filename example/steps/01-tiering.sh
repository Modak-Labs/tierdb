#!/usr/bin/env bash
# A TIERED table: old partitions move to Iceberg, reads stay transparent.
set -euo pipefail
source "$(dirname "$0")/../lib.sh"

say "1. Create a range-partitioned hot table and seed it (datasets/events.sql)"
$PSQL < example/datasets/events.sql
echo "   5 rows across 3 partitions; high-water event_time = 250"

say "2. Register the table with Modak (creates the cold Iceberg table)"
docker compose run --rm worker register --table public.events --pk id --tier-key event_time

say "3. Worker tiers everything behind the high-water mark"
wait_for "cut-line advanced to 200 (p0+p1 tiered)" \
    "SELECT tier_key_hi FROM modak.cutline WHERE table_id = 'public.events'::regclass::oid::bigint" \
    "200"
wait_for "tiered partitions physically dropped" \
    "SELECT count(*) FROM pg_inherits WHERE inhparent = 'public.events'::regclass" \
    "1"

echo "   plain SELECT still sees ALL rows (transparent two-tier read):"
$PSQL -c "SELECT * FROM public.events ORDER BY id"
echo "   ... while the raw heap holds only the recent partition:"
$PSQL -c "SET modak.transparent_reads = off; SELECT * FROM public.events ORDER BY id"

assert_eq "transparent read sees all rows" "5" \
    "$($PSQL -tA -c 'SELECT count(*) FROM public.events')"
assert_eq "raw heap holds only the hot partition" "1" \
    "$($PSQL -tA -c 'SET modak.transparent_reads = off; SELECT count(*) FROM public.events')"
