# Modak

Modak lets Postgres be the bounded, transactional hot tier of a table whose colder history lives in an open lakehouse (Iceberg), and queries across both tiers as one logical table with transactional-grade read consistency.

Each table chooses how the two share its rows. A **tiered** table keeps only its recent partitions in Postgres and moves the rest to Iceberg. A **mirrored** table keeps the full copy in Postgres while CDC trails every change into the lake, optionally shedding heap history it no longer needs hot. Either way, a thin open seam (a monotonic cut-line, a pinned lake snapshot, and a PK-keyed correction delta merged on read) stitches the tiers so every query sees a consistent point-in-time view, no duplicates and no gaps. Both tiers stay real, independently usable open systems: a Postgres you can run OLTP on, an Iceberg any engine can read. Modak owns only the glue.

## Repository layout

```
modak/
├── docs/          Documentation
├── example/       Scripted end-to-end walkthrough against the local stack
├── sql/           modak.* catalog schema (the cross-language contract)
├── extension/     The Postgres extension (Rust workspace, runs inside your Postgres)
│   └── crates/
│       ├── modak-core/   Pure consistency domain (no Postgres deps)
│       └── modak-pg/     pgrx extension: planner, write router, read-pin, pg_duckdb bridge
└── worker/        The worker fleet (Java Maven reactor, runs alongside Postgres)
    ├── modak-common/        Shared value types
    ├── modak-catalog/       Catalog facade over modak.* (JDBC)
    ├── modak-lake-api/      Pluggable lake ports (LakeWriter, LakeCommitter, ...)
    ├── modak-lake-iceberg/  iceberg-java implementation
    ├── modak-tiering/       Tiering worker (seal → flush → advance → reclaim)
    ├── modak-compaction/    Compaction worker (fold delta → cold)
    ├── modak-worker/        Headless daemon + CLI (the core deployable)
    └── modak-console/       Optional: worker + embedded web console in one binary
```

## Status

Functional end to end: tiering, compaction, transparent reads, and CDC-mirrored tables all work against the Dockerized stack (see [`docker/README.md`](docker/README.md) and [`example/`](example/README.md)). The worker embeds a web console (`http://localhost:9090` in the stack) with live per-table status and charts. Not yet production-hardened.

## Architecture at a glance

- **Read path:** Modak resolves the read-pin `(T, S, delta)` and rewrites the query into `recent (tier_key ≥ T) ⊕ cold-merge(Iceberg@S, delta)`. Execution runs in DuckDB via [`pg_duckdb`](https://github.com/duckdb/pg_duckdb). Modak owns planning, metadata, and consistency, DuckDB only executes.
- **Write path:** tiered tables route each record by its immutable tier-key vs the cut-line `T`. Recent rows go to the hot Postgres partition, older and backfill rows go to Iceberg (buffered via `modak.delta` and compaction). The tier-key decides, never the connector. Mirrored tables take plain DML and CDC does the rest.
- **Coordination:** the `modak` Postgres catalog. No cross-language RPC in v1.

## License

Apache-2.0. See [`LICENSE`](LICENSE).
