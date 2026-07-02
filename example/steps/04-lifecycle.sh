#!/usr/bin/env bash
# Lifecycle: kill an initial copy mid-flight, resume it, verify, unregister.
set -euo pipefail
source "$(dirname "$0")/../lib.sh"

say "12. A bigger mirrored table, and an initial copy we kill mid-flight (datasets/telemetry.sql)"
$PSQL < example/datasets/telemetry.sql
echo "   20000 rows; registering with --chunk-rows 500, then killing the copy"
docker compose run -d --name modak-example-copy worker register \
    --table public.telemetry --pk id --tier-key ts --mode mirrored --chunk-rows 500 >/dev/null
COPY_SEEN=0
for _ in $(seq 1 240); do
    CHUNKS=$($PSQL -tA -c "SELECT coalesce(max(chunks_done), -1) FROM modak.copy_progress
        WHERE table_id = 'public.telemetry'::regclass::oid::bigint")
    if [ "$CHUNKS" -ge 2 ]; then COPY_SEEN=1; break; fi
    sleep 0.5
done
if [ "$COPY_SEEN" != "1" ]; then
    docker logs modak-example-copy >&2 || true
    fail "timeout waiting for the initial copy to journal progress"
fi
docker kill modak-example-copy >/dev/null
docker rm modak-example-copy >/dev/null
KILLED_AT=$($PSQL -tA -c "SELECT chunks_done FROM modak.copy_progress
    WHERE table_id = 'public.telemetry'::regclass::oid::bigint")
echo "   killed after $KILLED_AT chunk(s); the journal row survives and the slot pins WAL"
[ -n "$KILLED_AT" ] || fail "copy_progress row missing after kill"

say "13. Re-running the same register resumes from the journal"
docker compose run --rm worker register \
    --table public.telemetry --pk id --tier-key ts --mode mirrored --chunk-rows 500
wait_for "copy finished (journal row cleared, frontier seeded)" \
    "SELECT (SELECT count(*) FROM modak.copy_progress WHERE table_id = 'public.telemetry'::regclass::oid::bigint) = 0
        AND (SELECT replicated_lsn IS NOT NULL FROM modak.cutline WHERE table_id = 'public.telemetry'::regclass::oid::bigint)" \
    "t"

say "14. verify: heap vs lake must match exactly (exits non-zero on mismatch)"
docker compose run --rm worker verify --table public.telemetry

say "15. unregister: catalog rows, slot, and publication all gone"
docker compose run --rm worker unregister --table public.telemetry --drop-lake
LEFTOVERS=$($PSQL -tA -c "
    SELECT (SELECT count(*) FROM modak.tables WHERE table_name = 'telemetry')
         + (SELECT count(*) FROM pg_replication_slots WHERE slot_name = 'modak_slot_public_telemetry')
         + (SELECT count(*) FROM pg_publication WHERE pubname = 'modak_pub_public_telemetry')")
assert_eq "unregister leaves nothing behind" "0" "$LEFTOVERS"
