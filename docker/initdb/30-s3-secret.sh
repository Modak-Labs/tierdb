#!/bin/bash
# Registers the warehouse S3 credentials as a pg_duckdb secret so iceberg_scan()
# can read s3:// locations. Endpoint is host:port (no scheme), e.g. minio:9000.
set -euo pipefail

if [ -z "${MODAK_S3_ENDPOINT:-}" ]; then
    echo "MODAK_S3_ENDPOINT not set; skipping DuckDB S3 secret"
    exit 0
fi

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<EOSQL
SELECT duckdb.create_simple_secret(
    type      := 'S3',
    key_id    := '${MODAK_S3_ACCESS_KEY:-minioadmin}',
    secret    := '${MODAK_S3_SECRET_KEY:-minioadmin}',
    region    := '${MODAK_S3_REGION:-us-east-1}',
    url_style := 'path',
    endpoint  := '${MODAK_S3_ENDPOINT}',
    use_ssl   := '${MODAK_S3_USE_SSL:-false}'
);
EOSQL
