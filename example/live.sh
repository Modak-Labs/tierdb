#!/usr/bin/env bash
# The example, kept running: run.sh proves the loop once and cleans up, this
# keeps two tables streaming so the console has something live to show. One
# table per warehouse (storage profiles), 24h of tiered history behind a
# moving frontier, and periodic corrections to cold rows feeding the delta.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

if [ "${1:-}" = "reset" ]; then
    say "L0. Offboarding the live tables"
    for t in sensor_readings trades; do
        docker compose run --rm worker unregister --table "public.$t" --drop-lake
    done
    $PSQL -c "DROP TABLE IF EXISTS public.sensor_readings, public.trades" >/dev/null
    exit 0
fi

if ! $PSQL -tA -c "SELECT 1 FROM modak.tables WHERE table_name = 'sensor_readings'" | grep -q 1; then
    say "L1. Two live tables with 24h of history (datasets/live.sql)"
    $PSQL -c "DROP TABLE IF EXISTS public.sensor_readings, public.trades" >/dev/null
    $PSQL < example/datasets/live.sql

    say "L2. A second warehouse: the 'analytics' storage profile"
    if ! docker compose run --rm worker profile list 2>/dev/null | grep -q "^analytics "; then
        docker compose run --rm worker profile create --name analytics \
            --warehouse s3://analytics/warehouse --credentials analytics
    fi

    say "L3. Register: sensor_readings on the default warehouse, trades on 'analytics'"
    docker compose run --rm worker register --table public.sensor_readings \
        --pk id --tier-key ts
    docker compose run --rm worker register --table public.trades \
        --pk id --tier-key ts --profile analytics

    wait_for "history tiered behind the frontier (sensor_readings)" \
        "SELECT count(*) > 20 FROM modak.partitions
          WHERE table_id = 'public.sensor_readings'::regclass::oid::bigint
            AND state = 'dropped'" \
        "t"
else
    say "Live tables already registered; resuming the stream"
fi

say "Streaming: 10 rows/table every 2s, a cold correction every ~30s. Ctrl-C to stop."
echo "   console: http://localhost:9090   (reset with: ./example/live.sh reset)"

i=0
while true; do
    $PSQL >/dev/null <<'SQL'
INSERT INTO public.sensor_readings
SELECT extract(epoch FROM now())::bigint * 10 + n, extract(epoch FROM now())::bigint,
       'device-' || (n % 7), 20 + 10 * sin(extract(epoch FROM now()) / 3600.0) + random()
FROM generate_series(0, 9) n;
INSERT INTO public.trades
SELECT extract(epoch FROM now())::bigint * 10 + n, extract(epoch FROM now())::bigint,
       (ARRAY['MODK','ICEB','DUCK','PGRS'])[1 + n % 4],
       100 + 20 * sin(extract(epoch FROM now()) / 7200.0) + random(), 1 + n
FROM generate_series(0, 9) n;
SQL
    if [ $((i % 15)) -eq 14 ]; then
        $PSQL >/dev/null <<'SQL'
WITH cold AS (SELECT (extract(epoch FROM now())::bigint - 12 * 3600) / 2 * 2 AS ts)
SELECT modak_upsert('public.sensor_readings'::regclass,
           jsonb_build_object('id', ts * 10, 'ts', ts,
                              'device', 'device-corrected', 'reading', -1)),
       modak_delete('public.trades'::regclass, to_jsonb(ts * 10), ts)
FROM cold;
SQL
        echo "   $(date '+%H:%M:%S') corrected cold rows from ~12h ago (delta, then compaction folds)"
    fi
    i=$((i + 1))
    sleep 2
done
