#!/usr/bin/env bash
# Make the example re-runnable: offboard the example tables and drop them.
set -euo pipefail
source "$(dirname "$0")/../lib.sh"

say "0. Preflight and cleanup of any previous run"

if [ "$($PSQL -tA -c 'SHOW wal_level')" != "logical" ]; then
    echo "wal_level is not 'logical' — the data volume predates mirrored-table support." >&2
    echo "Recreate the stack:  docker compose down -v && docker compose up -d --build" >&2
    exit 1
fi

docker rm -f modak-example-copy >/dev/null 2>&1 || true

# unregister offboards everything: catalog rows, CDC plumbing, the lake table.
for t in events vehicles telemetry; do
    docker compose run --rm worker unregister --table "public.$t" --drop-lake
done
$PSQL -c "DROP TABLE IF EXISTS public.events, public.vehicles, public.telemetry" >/dev/null

if [ "${EXAMPLE_REST:-0}" = "1" ]; then
    # The in-memory REST catalog desyncs from MinIO across restarts: wipe both.
    docker compose restart iceberg-rest >/dev/null
    docker compose run --rm --entrypoint /bin/sh minio-init -c \
        "mc alias set local http://minio:9000 minioadmin minioadmin >/dev/null \
         && mc rm -r --force local/warehouse/rest >/dev/null 2>&1; true"
    sleep 3
fi
