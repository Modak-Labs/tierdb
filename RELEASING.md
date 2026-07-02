# Releasing

Releases are cut from a version tag. Pushing `vX.Y.Z` runs
`.github/workflows/release.yml`, which packages the extension for Postgres
15, 16, and 17 on x86_64 and arm64 Linux, builds the worker and console jars,
and attaches everything to a GitHub release.

## Cutting a release

1. Bump the extension version in `extension/Cargo.toml`
   (`workspace.package.version`) and refresh the lockfile with
   `cargo update -w` from `extension/`.
2. Bump the worker to the same version by editing the `<revision>` property
   in `worker/pom.xml`.
3. If the extension's SQL surface changed since the last release, add an
   upgrade script (see below).
4. Commit, tag, push:

```bash
git tag vX.Y.Z
git push origin main vX.Y.Z
```

## Extension upgrade scripts

Installed clusters upgrade with `ALTER EXTENSION modak UPDATE`, and Postgres
walks a chain of upgrade scripts to get there. Every release that changes the
extension's SQL surface (functions, views, GUC-registering init code) must
ship `extension/crates/modak-pg/sql/modak--OLD--NEW.sql` containing just the
statements that take an OLD install to NEW. Releases that only change Rust
internals still need an empty upgrade script so the chain stays unbroken.

The catalog schema (`sql/catalog.sql`) versions separately. The worker applies
it at startup, so it needs no extension upgrade script.

## Installing a packaged build

Each tarball mirrors the layout `pg_config` reports, so it extracts onto the
filesystem root:

```bash
sudo tar -xzf modak-X.Y.Z-pg17-linux-x86_64.tar.gz -C /
```

Then set `shared_preload_libraries = 'pg_duckdb, modak'`, restart Postgres,
and `CREATE EXTENSION modak`.
