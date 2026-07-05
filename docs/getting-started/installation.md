# Installation

The [quickstart](quickstart.md) runs everything in Docker and configures all of this for you. This page is the manual path: installing Modak onto a Postgres you run yourself, from the packaged release artifacts.

## What you need

- Postgres 16 or 17 with the [pg_duckdb](https://github.com/duckdb/pg_duckdb) extension installed.
- The `modak` extension tarball for your Postgres version and platform, from the [releases page](https://github.com/Modak-Labs/modak/releases).
- Java 17 or newer for the worker, plus `modak-worker.jar` (or `modak-console.jar` for the version with the web console) from the same release.

## Install the extension

Each tarball mirrors the layout `pg_config` reports, so it extracts onto the filesystem root:

```bash
sudo tar -xzf modak-0.1.0-pg17-linux-x86_64.tar.gz -C /
```

That places `modak.so` in the library directory and `modak.control` plus the SQL script in the extension directory.

## Configure Postgres

In `postgresql.conf`, then restart the server:

```conf
shared_preload_libraries = 'pg_duckdb, modak'   # pg_duckdb first
wal_level = logical                             # mirrored tables need pgoutput
```

The order matters. Listing `pg_duckdb` first means modak's planner hook runs first, which transparent reads depend on.

## Set up the database

Create both extensions, then the DuckDB pieces the cold read path needs:

```sql
CREATE EXTENSION IF NOT EXISTS pg_duckdb;
CREATE EXTENSION modak;

-- DuckDB-side extensions for reading Iceberg over S3.
SELECT duckdb.install_extension('httpfs');
SELECT duckdb.install_extension('iceberg');

-- DuckDB parallel heap scans deadlock against PG parallel workers on mixed
-- plans, so keep them off.
ALTER SYSTEM SET duckdb.max_workers_per_postgres_scan = 0;

-- Transparent UPDATE/DELETE of cold rows runs the lake scan as a nested
-- statement, which pg_duckdb gates behind this setting.
ALTER SYSTEM SET duckdb.unsafe_allow_execution_inside_functions = 'on';
```

Give DuckDB credentials for the warehouse, so `iceberg_scan()` can read `s3://` locations. The endpoint is host and port with no scheme:

```sql
SELECT duckdb.create_simple_secret(
    type      := 'S3',
    key_id    := '...',
    secret    := '...',
    region    := 'us-east-1',
    url_style := 'path',
    endpoint  := 's3.us-east-1.amazonaws.com',
    use_ssl   := 'true'
);
```

Tables on additional warehouses (see [Storage profiles](../tables/storage-profiles.md)) each need a secret scoped to their warehouse root (`scope := 's3://analytics-lake/'`).

## Run the worker

The worker is a plain jar pointed at your database and object store through env vars:

```bash
MODAK_PG_URL='jdbc:postgresql://db.internal:5432/app' \
MODAK_PG_USER=modak_worker \
MODAK_PG_PASSWORD=... \
MODAK_WAREHOUSE=s3://analytics-lake/modak \
MODAK_S3_REGION=us-east-1 \
java -jar modak-worker.jar run
```

It creates the `modak.*` catalog schema on first start. From here, [register a table](../tables/registering-tables.md) and you are running. For roles, TLS, WAL safety, and everything else a shared database needs, see [Production deployment](../operations/production.md).
