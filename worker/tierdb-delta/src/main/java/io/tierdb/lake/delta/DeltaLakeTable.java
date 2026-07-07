package io.tierdb.lake.delta;

import io.delta.tables.DeltaTable;
import io.tierdb.common.LakeSnapshotId;
import io.tierdb.common.RowBatchData.Column;
import io.tierdb.lake.ColdTableSpec;
import io.tierdb.lake.LakeStats;
import io.tierdb.lake.LakeTable;
import io.tierdb.lake.TierKeyWindow;
import io.tierdb.lake.delta.commit.DeltaMergeWriter;
import io.tierdb.lake.delta.maintain.DeltaMaintenance;
import io.tierdb.lake.commit.LakeCommitResult;
import io.tierdb.lake.commit.MergeWriter;
import io.tierdb.lake.maintain.MaintenancePlan;
import io.tierdb.lake.maintain.MaintenanceResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

final class DeltaLakeTable implements LakeTable {

    private final DeltaTables tables;
    private final String path;
    private final ColdTableSpec spec;

    DeltaLakeTable(DeltaTables tables, String path, ColdTableSpec spec) {
        this.tables = tables;
        this.path = path;
        this.spec = spec;
    }

    @Override
    public MergeWriter mergeWriter() {
        return new DeltaMergeWriter(tables, path);
    }

    @Override
    public LakeStats stats() {
        return new DeltaStats(tables, path).collect();
    }

    @Override
    public void evolveSchema(List<Column> addColumns) {
        new DeltaSchemaEvolution(tables, path).addMissing(addColumns);
    }

    @Override
    public MaintenanceResult maintain(MaintenancePlan plan, Map<String, String> snapshotProps) {
        try {
            return new DeltaMaintenance(tables, path).run(plan, snapshotProps);
        } catch (RuntimeException e) {
            throw new IllegalStateException("maintenance failed for " + path, e);
        }
    }

    @Override
    public void deleteFiles(List<String> paths) {
        try {
            FileSystem fs = fs();
            for (String p : paths) {
                Path target = new Path(p);
                if (fs.exists(target)) {
                    fs.delete(target, false);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to delete staged files under " + path, e);
        }
    }

    @Override
    public LakeCommitResult expireBelow(long boundary, Map<String, String> snapshotProps) {
        SparkSession spark = tables.spark();
        long before = tables.latestVersion(path);
        DeltaCommit.withUserMetadata(spark, snapshotProps, () ->
                DeltaTable.forPath(spark, path).delete(
                        functions.col(spec.tierKeyCol()).lt(functions.lit(boundary))));
        long after = tables.latestVersion(path);
        if (after == before) {
            return null;
        }
        return LakeCommitResult.committedIsReadable(
                new LakeSnapshotId(after), DeltaPublish.props(path));
    }

    @Override
    public LakeCommitResult ingest(List<String> files, TierKeyWindow window,
            Map<String, String> snapshotProps) {
        if (files.isEmpty()) {
            return null;
        }
        SparkSession spark = tables.spark();
        Dataset<Row> df = spark.read().parquet(files.toArray(String[]::new));
        df.write().format("delta").mode("append")
                .option("userMetadata", DeltaJson.write(snapshotProps))
                .option("mergeSchema", "true")
                .save(path);
        return LakeCommitResult.committedIsReadable(
                new LakeSnapshotId(tables.latestVersion(path)), DeltaPublish.props(path));
    }

    @Override
    public List<String> stageRows(List<String> columns, Iterable<Object[]> rows) {
        SparkSession spark = tables.spark();
        StructType tableSchema = tables.load(path).toDF().schema();
        StructField[] fields = new StructField[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            fields[i] = tableSchema.apply(columns.get(i));
        }
        StructType schema = new StructType(fields);

        List<Row> staged = new ArrayList<>();
        for (Object[] row : rows) {
            Object[] values = new Object[row.length];
            for (int i = 0; i < row.length; i++) {
                values[i] = DeltaSchemas.coerce(row[i]);
            }
            staged.add(org.apache.spark.sql.RowFactory.create(values));
        }

        String stagingDir = tables.warehouse() + "/_staging/" + UUID.randomUUID();
        spark.createDataFrame(staged, schema).write().parquet(stagingDir);
        try {
            List<String> out = new ArrayList<>();
            FileSystem fs = fs();
            for (FileStatus status : fs.listStatus(new Path(stagingDir))) {
                if (status.getPath().getName().endsWith(".parquet")) {
                    out.add(status.getPath().toString());
                }
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("failed to list staged files under " + stagingDir, e);
        }
    }

    private FileSystem fs() throws java.io.IOException {
        Path p = new Path(path);
        return p.getFileSystem(tables.spark().sessionState().newHadoopConf());
    }
}
