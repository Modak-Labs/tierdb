package io.tierdb.lake.delta.commit;

import io.tierdb.common.LakeSnapshotId;
import io.tierdb.common.OpKind;
import io.tierdb.common.RowBatchData.Column;
import io.tierdb.lake.delta.DeltaJson;
import io.tierdb.lake.delta.DeltaPublish;
import io.tierdb.lake.delta.DeltaSchemaEvolution;
import io.tierdb.lake.delta.DeltaSchemas;
import io.tierdb.lake.delta.DeltaTables;
import io.tierdb.lake.commit.CommittedLakeSnapshot;
import io.tierdb.lake.commit.LakeCommitResult;
import io.tierdb.lake.commit.LakeCommitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

final class DeltaLakeCommitter implements LakeCommitter<DeltaWriteResult, DeltaCommittable> {

    private final DeltaTables tables;
    private final String path;

    DeltaLakeCommitter(DeltaTables tables, String path) {
        this.tables = tables;
        this.path = path;
    }

    @Override
    public DeltaCommittable toCommittable(List<DeltaWriteResult> results) {
        List<Row> rows = new ArrayList<>();
        List<Column> columns = null;
        for (DeltaWriteResult r : results) {
            if (columns == null && !r.columns().isEmpty()) {
                columns = r.columns();
            }
            rows.addAll(r.rows());
        }
        return rows.isEmpty() || columns == null ? null : new DeltaCommittable(columns, rows);
    }

    @Override
    public LakeCommitResult commit(DeltaCommittable committable, Map<String, String> snapshotProps)
            throws IOException {
        try {
            new DeltaSchemaEvolution(tables, path).addMissing(committable.columns());
            Dataset<Row> df = tables.spark().createDataFrame(
                    committable.rows(), DeltaSchemas.structType(committable.columns()));
            df.write().format("delta").mode("append")
                    .option("userMetadata", DeltaJson.write(snapshotProps))
                    .option("mergeSchema", "true")
                    .save(path);
            long version = tables.latestVersion(path);
            return LakeCommitResult.committedIsReadable(
                    new LakeSnapshotId(version), DeltaPublish.props(path));
        } catch (RuntimeException e) {
            throw new IOException("failed to commit to Delta table " + path, e);
        }
    }

    @Override
    public void abort(DeltaCommittable committable) {}

    @Override
    public Optional<CommittedLakeSnapshot> getMissingLakeSnapshot(LakeSnapshotId lastKnownInCatalog)
            throws IOException {
        return getMissingLakeSnapshot(lastKnownInCatalog, OpKind.TIERING);
    }

    @Override
    public Optional<CommittedLakeSnapshot> getMissingLakeSnapshot(LakeSnapshotId lastKnownInCatalog,
            OpKind opKind) throws IOException {
        if (!tables.exists(path)) {
            return Optional.empty();
        }
        Optional<DeltaHistory.Commit> found = DeltaHistory.latestOp(tables, path, opKind.sql(), null);
        if (found.isEmpty() || found.get().version() <= lastKnownInCatalog.id()) {
            return Optional.empty();
        }
        DeltaHistory.Commit commit = found.get();
        return Optional.of(new CommittedLakeSnapshot(
                new LakeSnapshotId(commit.version()),
                commit.props(),
                DeltaPublish.props(path)));
    }

    @Override
    public void close() {}
}
