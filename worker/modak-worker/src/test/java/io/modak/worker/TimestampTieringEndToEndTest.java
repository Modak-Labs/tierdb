package io.modak.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modak.catalog.CatalogSchema;
import io.modak.catalog.JdbcCatalog;
import io.modak.catalog.PartitionInfo;
import io.modak.catalog.RegisteredTable;
import io.modak.catalog.TableMode;
import io.modak.catalog.TableRegistration;
import io.modak.common.LakeSnapshotId;
import io.modak.common.PartitionId;
import io.modak.common.RowBatchData.Column;
import io.modak.common.RowBatchData.ColumnType;
import io.modak.common.TableId;
import io.modak.common.TierKey;
import io.modak.common.TierKeyType;
import io.modak.lake.LakePartition;
import io.modak.lake.iceberg.IcebergLakeStoragePlugin;
import io.modak.lake.iceberg.IcebergTableBootstrap;
import io.modak.lake.iceberg.IcebergTables;
import io.modak.tiering.JdbcHotSource;
import io.modak.tiering.PartitionSync;
import io.modak.tiering.TieringWorker;
import io.modak.tiering.policy.SealGatedEvictionPolicy;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end for a timestamptz tier key: daily heap partitions discovered from
 * pg_get_expr, aged into a day()-partitioned Iceberg table, cut-line canonical.
 */
class TimestampTieringEndToEndTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final TierKeyType TYPE = TierKeyType.TIMESTAMPTZ;

    @TempDir
    static Path warehouse;

    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;
    private static JdbcCatalog catalog;
    private static IcebergTables tables;
    private static String location;
    private static TableId table;

    @BeforeAll
    static void setUpWorld() throws IOException {
        postgres = EmbeddedPostgres.builder().start();
        dataSource = postgres.getPostgresDatabase();
        CatalogSchema.apply(dataSource);

        exec("""
                CREATE TABLE public.metrics (
                    id bigint NOT NULL, ts timestamptz NOT NULL, val text
                ) PARTITION BY RANGE (ts)
                """);
        exec("CREATE TABLE public.metrics_d0629 PARTITION OF public.metrics "
                + "FOR VALUES FROM ('2026-06-29 00:00:00+00') TO ('2026-06-30 00:00:00+00')");
        exec("CREATE TABLE public.metrics_d0630 PARTITION OF public.metrics "
                + "FOR VALUES FROM ('2026-06-30 00:00:00+00') TO ('2026-07-01 00:00:00+00')");
        exec("CREATE TABLE public.metrics_d0701 PARTITION OF public.metrics "
                + "FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-07-02 00:00:00+00')");
        exec("""
                INSERT INTO public.metrics VALUES
                  (1, '2026-06-29 08:00:00+00', 'a'),
                  (2, '2026-06-29 20:30:00+00', 'b'),
                  (3, '2026-06-30 12:00:00+00', 'c'),
                  (4, '2026-07-01 01:00:00+00', 'hot'),
                  (5, '2026-07-01 09:00:00+00', 'hot')
                """);

        tables = IcebergTables.from(Map.of(), new Configuration());
        location = warehouse.resolve("metrics_cold").toString();
        IcebergTableBootstrap.createIfAbsent(tables, location,
                List.of(new Column("id", ColumnType.LONG),
                        new Column("ts", ColumnType.TIMESTAMP),
                        new Column("val", ColumnType.TEXT)),
                Set.of("id", "ts"), "ts", LakePartition.temporal("day"));

        catalog = new JdbcCatalog(dataSource);
        table = catalog.register(new TableRegistration(
                relOid("public.metrics"), "public", "metrics", List.of("id"), "ts",
                "{\"unit\":\"range\",\"partition_width\":86400000000,\"lake_transform\":\"day\"}",
                IcebergLakeStoragePlugin.IDENTIFIER, location,
                TableMode.TIERED, null, null, java.util.Optional.empty(),
                java.util.Optional.empty(), false, "default", TYPE));
        catalog.initCutline(table, new TierKey(encode("2026-06-29 00:00:00+00")),
                new LakeSnapshotId(0));
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void timestampPartitionsAgeDownIntoDayPartitionedIceberg() throws Exception {
        RegisteredTable meta = catalog.get(table).orElseThrow();
        assertEquals(TYPE, meta.tierKeyType());

        int discovered = new PartitionSync(dataSource, catalog).sync(meta);
        assertEquals(3, discovered);
        List<PartitionInfo> partitions = new ArrayList<>(catalog.listPartitions(table));
        partitions.sort((a, b) -> a.bounds().lo().compareTo(b.bounds().lo()));
        assertEquals(encode("2026-06-29 00:00:00+00"), partitions.get(0).bounds().lo().value());
        assertEquals(encode("2026-06-30 00:00:00+00"), partitions.get(0).bounds().hi().value());
        assertEquals(encode("2026-07-02 00:00:00+00"), partitions.get(2).bounds().hi().value());

        List<PartitionId> cold = List.of(partitions.get(0).id(), partitions.get(1).id());
        TieringWorker worker = new TieringWorker(
                catalog,
                new IcebergLakeStoragePlugin().create(Map.of()),
                new JdbcHotSource(dataSource),
                (t, now) -> cold,
                new SealGatedEvictionPolicy());
        worker.runCycle(table, NOW);

        assertEquals(List.of(
                        "1|2026-06-29T08:00Z|a",
                        "2|2026-06-29T20:30Z|b",
                        "3|2026-06-30T12:00Z|c"),
                lakeRows());
        Table iceberg = tables.load(location);
        assertEquals("day", iceberg.spec().fields().get(0).transform().toString());

        assertEquals(encode("2026-07-01 00:00:00+00"),
                catalog.readCutline(table).t().value());

        assertNull(queryOne("SELECT to_regclass('public.metrics_d0629')::text"), "d0629 dropped");
        assertNull(queryOne("SELECT to_regclass('public.metrics_d0630')::text"), "d0630 dropped");
        assertEquals("2", queryOne("SELECT count(*)::text FROM public.metrics"));
        assertTrue(iceberg.currentSnapshot().summary()
                .get("modak.new-tier-key-hi").equals(Long.toString(encode("2026-07-01 00:00:00+00"))));
    }

    private static long encode(String literal) {
        return TYPE.encode(literal);
    }

    private static List<String> lakeRows() throws IOException {
        List<String> rows = new ArrayList<>();
        Table iceberg = tables.load(location);
        try (CloseableIterable<Record> records = IcebergGenerics.read(iceberg).build()) {
            for (Record r : records) {
                rows.add(r.getField("id") + "|" + r.getField("ts") + "|" + r.getField("val"));
            }
        }
        rows.sort(String::compareTo);
        return rows;
    }

    private static long relOid(String qualified) {
        return Long.parseLong(queryOne("SELECT '" + qualified + "'::regclass::oid::bigint::text"));
    }

    private static void exec(String sql) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String queryOne(String sql) {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
