#!/usr/bin/env bash
# A MIRRORED table: full copy stays in Postgres, CDC trails a copy in Iceberg.
set -euo pipefail
source "$(dirname "$0")/../lib.sh"

say "7. Create an ordinary (unpartitioned) table and seed it (datasets/vehicles.sql)"
$PSQL < example/datasets/vehicles.sql
echo "   3 vehicles; these predate registration and travel via the initial copy"

say "8. Register it MIRRORED (publication + slot + initial copy to Iceberg)"
docker compose run --rm worker register \
    --table public.vehicles --pk id --tier-key last_seen --mode mirrored

say "9. Plain DML — no Modak API involved — and the mirror trails it"
$PSQL <<'SQL'
INSERT INTO public.vehicles VALUES (4, 'VIN-004', 'active', 260);
UPDATE public.vehicles SET status = 'repair', last_seen = 210 WHERE id = 2;
DELETE FROM public.vehicles WHERE id = 3;
SQL
TARGET_LSN=$($PSQL -tA -c "SELECT (pg_current_wal_insert_lsn() - '0/0'::pg_lsn)::bigint")
wait_for "mirror frontier caught up past the DML (lsn $TARGET_LSN)" \
    "SELECT replicated_lsn >= $TARGET_LSN
       FROM modak.cutline WHERE table_id = 'public.vehicles'::regclass::oid::bigint" \
    "t"

EXPECTED="1|VIN-001|active|100
2|VIN-002|repair|210
4|VIN-004|active|260"

say "10. Same rows from the heap and from Iceberg (hybrid read)"
echo "   default read — the heap alone, it is complete:"
HEAP=$($PSQL -tA -c \
    "SELECT string_agg(id::text || '|' || vin || '|' || status || '|' || last_seen::text, E'\n' ORDER BY id) FROM public.vehicles")
echo "$HEAP"
assert_eq "mirrored heap read" "$EXPECTED" "$HEAP"

echo "   hybrid read — the same query served from the Iceberg mirror:"
HYBRID=$($PSQL -tA <<'SQL'
SET modak.mirrored_reads = 'hybrid';
SET duckdb.max_workers_per_postgres_scan = 0;
SELECT string_agg(id::text || '|' || vin || '|' || status || '|' || last_seen::text, E'\n' ORDER BY id) FROM public.vehicles;
SQL
)
echo "$HYBRID"
assert_eq "mirrored hybrid read" "$EXPECTED" "$HYBRID"

say "11. Cross-mode join: tiered events (two tiers) x mirrored vehicles"
JOIN=$($PSQL -tA -c \
    "SELECT string_agg(e.id::text || '|' || coalesce(e.val,'') || '|' || v.vin, E'\n' ORDER BY e.id)
       FROM public.events e JOIN public.vehicles v ON v.id = e.id")
echo "$JOIN"
assert_eq "cross-mode join" "2|B?|VIN-002" "$JOIN"
