package io.tierdb.lake.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.tierdb.common.DeltaRowsBatch;
import io.tierdb.common.LakeSnapshotId;
import io.tierdb.common.OpKind;
import io.tierdb.common.PartitionBounds;
import io.tierdb.common.PartitionId;
import io.tierdb.common.RowBatchData;
import io.tierdb.common.RowBatchData.Column;
import io.tierdb.common.RowBatchData.ColumnType;
import io.tierdb.common.TableId;
import io.tierdb.common.TierKey;
import io.tierdb.lake.LakePartition;
import io.tierdb.lake.LakeStoragePlugin;
import io.tierdb.lake.delta.commit.DeltaCommittable;
import io.tierdb.lake.delta.commit.DeltaMergeWriter;
import io.tierdb.lake.delta.commit.DeltaTieringFactory;
import io.tierdb.lake.delta.commit.DeltaWriteResult;
import io.tierdb.lake.delta.maintain.DeltaMaintenance;
import io.tierdb.lake.commit.CommitterInitContext;
import io.tierdb.lake.commit.CommittedLakeSnapshot;
import io.tierdb.lake.commit.LakeCommitResult;
import io.tierdb.lake.commit.LakeCommitter;
import io.tierdb.lake.commit.LakeTieringProps;
import io.tierdb.lake.commit.LakeWriter;
import io.tierdb.lake.maintain.MaintenancePlan;
import io.tierdb.lake.maintain.MaintenanceResult;
import io.tierdb.lake.commit.WriterInitContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeltaLakeStorageTest {

    private static final TableId TABLE = new TableId(42);
    private static final List<Column> COLUMNS = List.of(
            new Column("id", ColumnType.LONG),
            new Column("ts", ColumnType.LONG),
            new Column("val", ColumnType.TEXT));

    @TempDir
    Path warehouse;

    private DeltaLakeStorage storage;
    private String ref;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        storage = new DeltaLakeStorage(Map.of("warehouse", warehouse.toString()));
        ref = storage.tableRef("public", "events");
        storage.createTableIfAbsent(ref, COLUMNS, Set.of("id", "ts"), "ts", LakePartition.none());
    }

    @Test
    void deltaPluginIsDiscoverableAndKeyedByIdentifier() {
        var plugin = ServiceLoader.load(LakeStoragePlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> DeltaLakeStoragePlugin.IDENTIFIER.equals(p.identifier()))
                .findFirst();
        assertTrue(plugin.isPresent(),
                "delta LakeStoragePlugin must be registered via META-INF/services");
    }

    @Test
    void tableRefIsTheWarehousePath() {
        assertEquals(warehouse + "/public.events", ref);
    }

    @Test
    void tieringCommitsAdvanceVersionAndAreReadable() throws Exception {
        LakeCommitResult result = commit(5, 105);
        assertTrue(result.readable().id() >= 1, "the append lands at a Delta version past 0");
        assertEquals(2, rowCount());
    }

    @Test
    void mergeFoldsUpsertsAndTombstonesNewestWins() throws Exception {
        commit(5, 10);
        DeltaRowsBatch delta = new DeltaRowsBatch(
                TABLE, List.of("id"), COLUMNS, List.of(
                        new DeltaRowsBatch.Entry("1", false, 5, 2, new Object[] {1L, 5L, "updated"}),
                        new DeltaRowsBatch.Entry("2", true, 10, 2, null)));
        new DeltaMergeWriter(storage.tables(), ref).applyDelta(delta, props(OpKind.COMPACTION));

        assertEquals(1, rowCount(), "the tombstone dropped id=2");
        assertEquals("updated", stringValue(1L, "val"), "the upsert replaced id=1's image");
    }

    @Test
    void optimizeCompactsSmallFilesWithoutChangingRows() throws Exception {
        commit(5);
        commit(10);
        commit(15);
        long filesBefore = numFiles();

        Map<String, String> settings = new HashMap<>();
        settings.put("vacuum_enabled", "false");
        MaintenanceResult result = new DeltaMaintenance(storage.tables(), ref)
                .run(new MaintenancePlan(settings, Long.MAX_VALUE, List.of()),
                        props(OpKind.MAINTENANCE));

        assertTrue(result.counter("optimize_removed_files") >= 1,
                "OPTIMIZE bin-packs the small per-commit files");
        assertTrue(numFiles() < filesBefore, "the file count drops after compaction");
        assertEquals(3, rowCount(), "compaction must not change the data");
    }

    @Test
    void crashResumeFindsTheCommittedSnapshotByOpKind() throws Exception {
        Map<String, String> commitProps = props(OpKind.TIERING);
        commitProps.put(LakeTieringProps.NEW_TIER_KEY_HI, "999");
        commitOne(commitProps, 5, 10);

        try (LakeCommitter<DeltaWriteResult, DeltaCommittable> committer =
                new DeltaTieringFactory(storage.tables())
                        .createCommitter(new CommitterInitContext(TABLE, ref))) {
            Optional<CommittedLakeSnapshot> found =
                    committer.getMissingLakeSnapshot(new LakeSnapshotId(0));
            assertTrue(found.isPresent(), "the tiering commit must be recoverable after a crash");
            assertTrue(found.get().readable().id() >= 1);
            assertEquals("999", found.get().snapshotProps().get(LakeTieringProps.NEW_TIER_KEY_HI));
            assertEquals(warehouse + "/public.events",
                    found.get().publishProps().get("table_location"));
        }
    }

    private LakeCommitResult commit(long... tierKeys) throws Exception {
        return commitOne(props(OpKind.TIERING), tierKeys);
    }

    private LakeCommitResult commitOne(Map<String, String> commitProps, long... tierKeys)
            throws Exception {
        DeltaTieringFactory factory = new DeltaTieringFactory(storage.tables());
        PartitionId pid = new PartitionId(TABLE, "p_" + nextId);
        PartitionBounds bounds = new PartitionBounds(new TierKey(0), new TierKey(1000));
        List<Object[]> rows = new ArrayList<>();
        for (long ts : tierKeys) {
            rows.add(new Object[] {nextId++, ts, "v"});
        }
        DeltaWriteResult result;
        try (LakeWriter<DeltaWriteResult> writer =
                factory.createWriter(new WriterInitContext(TABLE, pid, bounds, ref))) {
            writer.write(new RowBatchData(pid, bounds, COLUMNS, rows));
            result = writer.complete();
        }
        try (LakeCommitter<DeltaWriteResult, DeltaCommittable> committer =
                factory.createCommitter(new CommitterInitContext(TABLE, ref))) {
            return committer.commit(committer.toCommittable(List.of(result)), commitProps);
        }
    }

    private static Map<String, String> props(OpKind kind) {
        Map<String, String> props = new HashMap<>();
        props.put(LakeTieringProps.OP_ID, UUID.randomUUID().toString());
        props.put(LakeTieringProps.OP_KIND, kind.sql());
        props.put(LakeTieringProps.COMMIT_USER, kind.commitUser());
        return props;
    }

    private long rowCount() {
        return storage.tables().load(ref).toDF().count();
    }

    private long numFiles() {
        var detail = storage.tables().load(ref).detail().collectAsList().get(0);
        int idx = detail.fieldIndex("numFiles");
        return detail.isNullAt(idx) ? 0 : detail.getLong(idx);
    }

    private String stringValue(long id, String column) {
        var rows = storage.tables().load(ref).toDF()
                .filter("id = " + id)
                .select(column)
                .collectAsList();
        return rows.isEmpty() ? null : rows.get(0).getString(0);
    }
}
