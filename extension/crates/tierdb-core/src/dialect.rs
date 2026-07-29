use crate::dml::{lake_delete_by_pk_sql, lake_insert_sql};
use crate::domain::TierKey;
use crate::read::Cold;
use crate::sqlgen::{column_list, ident, lit};
use crate::table::Table;
use crate::{Result, TierDBError};

pub trait SqlDialect {
    fn heap_from(&self, meta: &Table) -> String;
    fn hot_projection(&self, meta: &Table) -> String;
    fn lake_base(&self, meta: &Table, cold: &Cold) -> Result<String>;
    fn delta_from(&self) -> String;
    fn delta_projection(&self, meta: &Table) -> String;

    fn lake_upsert(&self, meta: &Table, cold: &Cold, rows: &[Vec<String>]) -> Result<Vec<String>>;

    fn lake_delete(
        &self,
        meta: &Table,
        cold: &Cold,
        pk_rows: &[Vec<String>],
        tier_lt: Option<TierKey>,
    ) -> Result<String>;
}

fn tier_bound(meta: &Table, tier_lt: Option<TierKey>) -> Option<String> {
    tier_lt.map(|t| {
        format!(
            "t.{} < {}",
            ident(&meta.tier_key_col),
            meta.tier_key_type.pg_literal(t.0),
        )
    })
}

fn column_names(meta: &Table) -> Vec<String> {
    meta.columns.iter().map(|c| c.name.clone()).collect()
}

fn pk_projection(meta: &Table, rows: &[Vec<String>]) -> Result<Vec<Vec<String>>> {
    let indices: Vec<usize> = meta
        .pk_cols
        .iter()
        .map(|pk| {
            meta.columns
                .iter()
                .position(|c| &c.name == pk)
                .ok_or_else(|| {
                    TierDBError::Planning(format!("primary key column '{pk}' is not in the row"))
                })
        })
        .collect::<Result<_>>()?;
    Ok(rows
        .iter()
        .map(|r| indices.iter().map(|&i| r[i].clone()).collect())
        .collect())
}

pub struct PgDuckdb;

impl SqlDialect for PgDuckdb {
    fn heap_from(&self, meta: &Table) -> String {
        format!("{}.{}", ident(&meta.schema), ident(&meta.name))
    }

    fn hot_projection(&self, meta: &Table) -> String {
        column_list(meta)
    }

    fn lake_base(&self, meta: &Table, cold: &Cold) -> Result<String> {
        crate::sqlgen::lake_base_select(meta, cold)
    }

    fn delta_from(&self) -> String {
        "tierdb.delta".to_string()
    }

    fn delta_projection(&self, meta: &Table) -> String {
        crate::sqlgen::pg_delta_projection(meta)
    }

    // pg_duckdb's bundled DuckDB predates MERGE-into-Iceberg, so this is a
    // DELETE-then-INSERT with the same by-pk semantics.
    fn lake_upsert(&self, meta: &Table, cold: &Cold, rows: &[Vec<String>]) -> Result<Vec<String>> {
        let target = cold.write_target()?;
        let pk_rows = pk_projection(meta, rows)?;
        Ok(vec![
            lake_delete_by_pk_sql(&target, &meta.pk_cols, &pk_rows, None),
            lake_insert_sql(&target, &column_names(meta), rows),
        ])
    }

    fn lake_delete(
        &self,
        meta: &Table,
        cold: &Cold,
        pk_rows: &[Vec<String>],
        tier_lt: Option<TierKey>,
    ) -> Result<String> {
        let target = cold.write_target()?;
        let bound = tier_bound(meta, tier_lt);
        Ok(lake_delete_by_pk_sql(
            &target,
            &meta.pk_cols,
            pk_rows,
            bound.as_deref(),
        ))
    }
}

pub fn duckdb_literal(value: &serde_json::Value, duckdb_ty: &str) -> String {
    use serde_json::Value;
    match value {
        Value::Null => "NULL".to_string(),
        Value::Bool(b) => format!("CAST({b} AS {duckdb_ty})"),
        Value::Number(n) => format!("CAST({n} AS {duckdb_ty})"),
        Value::String(s) => format!("CAST({} AS {duckdb_ty})", lit(s)),
        other => format!("CAST({} AS {duckdb_ty})", lit(&other.to_string())),
    }
}

pub fn lake_row_tuple(row: &serde_json::Value, columns: &[crate::sqlgen::Column]) -> Vec<String> {
    columns
        .iter()
        .map(|c| {
            duckdb_literal(
                row.get(&c.name).unwrap_or(&serde_json::Value::Null),
                &duckdb_type(&c.sql_type),
            )
        })
        .collect()
}

pub fn duckdb_type(pg_type: &str) -> String {
    let t = pg_type.trim().to_ascii_lowercase();
    if let Some(elem) = t.strip_suffix("[]") {
        return format!("{}[]", duckdb_type(elem.trim()));
    }
    let head = t.split('(').next().unwrap_or(&t).trim();
    match head {
        "bigint" | "int8" => "BIGINT".to_string(),
        "integer" | "int" | "int4" => "INTEGER".to_string(),
        "smallint" | "int2" => "SMALLINT".to_string(),
        "boolean" | "bool" => "BOOLEAN".to_string(),
        "numeric" | "decimal" => decimal_type(&t),
        "double precision" | "float8" => "DOUBLE".to_string(),
        "real" | "float4" => "REAL".to_string(),
        "date" => "DATE".to_string(),
        "time with time zone" | "timetz" => "TIME WITH TIME ZONE".to_string(),
        "time" | "time without time zone" => "TIME".to_string(),
        "timestamp with time zone" | "timestamptz" => "TIMESTAMP WITH TIME ZONE".to_string(),
        "timestamp" | "timestamp without time zone" => "TIMESTAMP".to_string(),
        "uuid" => "UUID".to_string(),
        "bytea" => "BLOB".to_string(),
        _ => "VARCHAR".to_string(),
    }
}

fn decimal_type(t: &str) -> String {
    let args = t.split('(').nth(1).and_then(|s| s.split(')').next());
    if let Some(args) = args {
        let mut parts = args.split(',').map(|s| s.trim());
        let precision = parts.next().and_then(|s| s.parse::<u32>().ok());
        let scale = parts
            .next()
            .and_then(|s| s.parse::<u32>().ok())
            .unwrap_or(0);
        if let Some(precision) = precision {
            if (1..=38).contains(&precision) && scale <= precision {
                return format!("DECIMAL({precision},{scale})");
            }
        }
    }
    "DOUBLE".to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::{TableId, TierKey};
    use crate::mode::Mode;
    use crate::read::Cold;
    use crate::sqlgen::Column;
    use crate::TierKeyType;
    use std::collections::BTreeMap;

    fn meta() -> Table {
        Table {
            id: TableId(90001),
            schema: "public".into(),
            name: "events".into(),
            columns: vec![
                Column {
                    name: "id".into(),
                    sql_type: "bigint".into(),
                },
                Column {
                    name: "event_time".into(),
                    sql_type: "bigint".into(),
                },
                Column {
                    name: "val".into(),
                    sql_type: "text".into(),
                },
            ],
            pk_cols: vec!["id".into()],
            tier_key_col: "event_time".into(),
            tier_key_type: TierKeyType::Bigint,
            mode: Mode::Tiered { keep_heap: false },
            lake_format: "iceberg".into(),
            lake_table_ref: Some("tierdb.public_events".into()),
            catalog: None,
        }
    }

    fn merge(path: &str) -> Cold {
        Cold::Merge {
            props: BTreeMap::from([("metadata_location".into(), path.into())]),
        }
    }

    fn live() -> Cold {
        Cold::Live {
            catalog: "__tierdb_lake_default".into(),
            table_ref: "tierdb.public_events".into(),
        }
    }

    fn rows() -> Vec<Vec<String>> {
        vec![
            vec!["1".into(), "50".into(), "'a'".into()],
            vec!["2".into(), "60".into(), "'b'".into()],
        ]
    }

    #[test]
    fn pg_lake_upsert_is_delete_then_insert_with_the_same_semantics() {
        let stmts = PgDuckdb.lake_upsert(&meta(), &live(), &rows()).unwrap();
        assert_eq!(
            stmts,
            vec![
                "DELETE FROM \"__tierdb_lake_default\".\"tierdb\".\"public_events\" AS t \
                 WHERE t.\"id\" IN (1, 2)"
                    .to_string(),
                "INSERT INTO \"__tierdb_lake_default\".\"tierdb\".\"public_events\" \
                 (\"id\", \"event_time\", \"val\") VALUES (1, 50, 'a'), (2, 60, 'b')"
                    .to_string(),
            ]
        );
    }

    #[test]
    fn lake_delete_bounds_to_the_cold_side() {
        let pk_rows = vec![vec!["1".to_string()], vec!["2".to_string()]];
        let sql = PgDuckdb
            .lake_delete(&meta(), &live(), &pk_rows, Some(TierKey(100)))
            .unwrap();
        assert_eq!(
            sql,
            "DELETE FROM \"__tierdb_lake_default\".\"tierdb\".\"public_events\" AS t \
             WHERE t.\"id\" IN (1, 2) AND t.\"event_time\" < 100"
        );
    }

    #[test]
    fn lake_delete_uses_row_values_for_composite_keys() {
        let mut m = meta();
        m.pk_cols = vec!["id".into(), "val".into()];
        let pk_rows = vec![
            vec!["1".to_string(), "'x'".to_string()],
            vec!["2".to_string(), "'y'".to_string()],
        ];
        let sql = PgDuckdb.lake_delete(&m, &live(), &pk_rows, None).unwrap();
        assert_eq!(
            sql,
            "DELETE FROM \"__tierdb_lake_default\".\"tierdb\".\"public_events\" AS t \
             WHERE (t.\"id\", t.\"val\") IN ((1, 'x'), (2, 'y'))"
        );
    }

    #[test]
    fn lake_dml_rejects_a_pinned_snapshot() {
        let pin = merge("/wh/m.json");
        assert!(PgDuckdb.lake_upsert(&meta(), &pin, &rows()).is_err());
        assert!(PgDuckdb
            .lake_delete(&meta(), &pin, &[vec!["1".into()]], None)
            .is_err());
    }

    #[test]
    fn literals_cast_to_the_columns_duckdb_type() {
        use serde_json::json;
        assert_eq!(duckdb_literal(&serde_json::Value::Null, "BIGINT"), "NULL");
        assert_eq!(duckdb_literal(&json!(42), "BIGINT"), "CAST(42 AS BIGINT)");
        assert_eq!(
            duckdb_literal(&json!(true), "BOOLEAN"),
            "CAST(true AS BOOLEAN)"
        );
        assert_eq!(
            duckdb_literal(&json!("o'clock"), "VARCHAR"),
            "CAST('o''clock' AS VARCHAR)"
        );
        assert_eq!(
            duckdb_literal(&json!([1, 2]), "INTEGER[]"),
            "CAST('[1,2]' AS INTEGER[])"
        );
    }

    #[test]
    fn row_tuples_follow_the_columns_and_null_fill_gaps() {
        let row = serde_json::json!({"id": 7, "val": "a"});
        assert_eq!(
            lake_row_tuple(&row, &meta().columns),
            vec!["CAST(7 AS BIGINT)", "NULL", "CAST('a' AS VARCHAR)"]
        );
    }

    #[test]
    fn numeric_preserves_precision() {
        assert_eq!(duckdb_type("numeric(10,2)"), "DECIMAL(10,2)");
        assert_eq!(duckdb_type("numeric(12)"), "DECIMAL(12,0)");
        assert_eq!(duckdb_type("numeric"), "DOUBLE");
        assert_eq!(duckdb_type("numeric(50,10)"), "DOUBLE");
        assert_eq!(duckdb_type("character varying(255)"), "VARCHAR");
        assert_eq!(
            duckdb_type("timestamp with time zone"),
            "TIMESTAMP WITH TIME ZONE"
        );
    }

    #[test]
    fn arrays_map_to_native_lists() {
        assert_eq!(duckdb_type("integer[]"), "INTEGER[]");
        assert_eq!(duckdb_type("text[]"), "VARCHAR[]");
        assert_eq!(duckdb_type("numeric(12,3)[]"), "DECIMAL(12,3)[]");
    }
}
