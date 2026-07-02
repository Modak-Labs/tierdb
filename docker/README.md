# Local test stack

A docker compose harness for experimenting with Modak end to end. This is not
a production topology. MinIO stands in for S3 and one worker container stands
in for wherever your workers would really run. In production the same worker
binary points at your managed Postgres and real object store via the same env
vars, see the [production deployment guide](../docs/guides/production.md).

| Service    | Role                                                                   |
|------------|------------------------------------------------------------------------|
| `postgres` | Postgres 17 + `pg_duckdb` + the `modak` extension + `modak.*` catalog  |
| `minio`    | S3-compatible Iceberg warehouse (`s3://warehouse`)                     |
| `worker`   | The daemon (console binary): tiering, mirroring, compaction            |

## Quickstart

```bash
docker compose up -d --build     # first build compiles the extension (~minutes)
./example/run.sh                 # scripted walkthrough (see example/README.md)
```

Poke around:

```bash
psql postgres://postgres:modak@localhost:5432/postgres   # the database
open http://localhost:9090                               # the Modak console
open http://localhost:9001                               # MinIO (minioadmin/minioadmin)
docker compose logs -f worker                            # cycle-by-cycle log
```

CLI commands run through the worker service, e.g.:

```bash
docker compose run --rm worker register --table public.my_table --pk id --tier-key event_time
```

Everything else (table modes, reading, configuration, operations) is in the
[docs](../docs/index.md).

## Images

`docker/Dockerfile.postgres` builds the Postgres image: pg_duckdb base plus the
`modak` extension compiled in a builder stage, provisioned on first boot by the
`initdb/` scripts (extensions, then catalog schema, then DuckDB S3 secret).

`docker/Dockerfile.worker` builds the worker image. The default binary is
`modak-console` (worker + web console), and
`--build-arg MODAK_BINARY=modak-worker` builds the headless one.

## Iceberg REST catalog variant

By default lake tables are path-based under `s3://warehouse`. An overlay adds a
bundled REST catalog server and routes new tables through it:

```bash
docker compose -f docker-compose.yml -f docker-compose.rest.yml up -d --build
EXAMPLE_REST=1 ./example/run.sh     # same walkthrough, tables via the catalog
```

## Teardown

```bash
docker compose down -v    # removes all data
```
