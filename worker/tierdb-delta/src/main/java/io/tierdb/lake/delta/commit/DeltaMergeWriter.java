package io.tierdb.lake.delta.commit;

import io.delta.tables.DeltaTable;
import io.tierdb.common.DeltaBatch;
import io.tierdb.common.DeltaRowsBatch;
import io.tierdb.common.LakeSnapshotId;
import io.tierdb.common.PgValues;
import io.tierdb.common.RowBatchData.Column;
import io.tierdb.lake.delta.DeltaCommit;
import io.tierdb.lake.delta.DeltaPublish;
import io.tierdb.lake.delta.DeltaSchemaEvolution;
import io.tierdb.lake.delta.DeltaSchemas;
import io.tierdb.lake.delta.DeltaTables;
import io.tierdb.lake.commit.LakeCommitResult;
import io.tierdb.lake.commit.MergeWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

public final class DeltaMergeWriter implements MergeWriter {

    private static final String OP = "__tierdb_op";

    private final DeltaTables tables;
    private final String path;

    public DeltaMergeWriter(DeltaTables tables, String path) {
        this.tables = tables;
        this.path = path;
    }

    @Override
    public LakeCommitResult applyDelta(DeltaBatch batch, Map<String, String> snapshotProps)
            throws IOException {
        if (!(batch instanceof DeltaRowsBatch rows)) {
            throw new IOException("Delta merge writer expects DeltaRowsBatch, got "
                    + batch.getClass().getName());
        }
        new DeltaSchemaEvolution(tables, path).addMissing(rows.columns());

        Map<String, DeltaRowsBatch.Entry> newest = new LinkedHashMap<>();
        for (DeltaRowsBatch.Entry e : rows.entries()) {
            DeltaRowsBatch.Entry prev = newest.get(e.pk());
            if (prev == null || e.version() >= prev.version()) {
                newest.put(e.pk(), e);
            }
        }

        List<Column> columns = rows.columns();
        List<Row> source = new ArrayList<>(newest.size());
        int[] pkIndexes = pkIndexes(columns, rows.pkColumns());
        for (DeltaRowsBatch.Entry e : newest.values()) {
            source.add(sourceRow(e, columns, rows.pkColumns(), pkIndexes));
        }

        Dataset<Row> df = tables.spark().createDataFrame(source, sourceSchema(columns));
        DeltaTable table = tables.load(path);

        Map<String, String> assignments = new LinkedHashMap<>();
        for (Column col : columns) {
            assignments.put(col.name(), "s.`" + col.name() + "`");
        }
        String condition = mergeCondition(rows.pkColumns());

        DeltaCommit.withUserMetadata(tables.spark(), snapshotProps, () ->
                table.as("t").merge(df.as("s"), condition)
                        .whenMatched("s.`" + OP + "` = 1").delete()
                        .whenMatched().updateExpr(assignments)
                        .whenNotMatched("s.`" + OP + "` = 0").insertExpr(assignments)
                        .execute());

        long version = tables.latestVersion(path);
        return LakeCommitResult.committedIsReadable(
                new LakeSnapshotId(version), DeltaPublish.props(path));
    }

    private Row sourceRow(DeltaRowsBatch.Entry e, List<Column> columns,
            List<String> pkColumns, int[] pkIndexes) throws IOException {
        Object[] values = new Object[columns.size() + 1];
        if (e.tombstone() && e.row() == null) {
            if (pkColumns.size() != 1) {
                throw new IOException("composite-pk tombstone for '" + e.pk()
                        + "' has no row image; cannot fold into " + path);
            }
            Column pkCol = columns.get(pkIndexes[0]);
            values[pkIndexes[0]] = DeltaSchemas.coerce(PgValues.parseText(e.pk(), pkCol.type()));
        } else {
            for (int i = 0; i < columns.size(); i++) {
                values[i] = DeltaSchemas.coerce(e.row()[i]);
            }
        }
        values[columns.size()] = e.tombstone() ? 1 : 0;
        return RowFactory.create(values);
    }

    private static int[] pkIndexes(List<Column> columns, List<String> pkColumns)
            throws IOException {
        int[] idx = new int[pkColumns.size()];
        for (int i = 0; i < pkColumns.size(); i++) {
            idx[i] = -1;
            for (int c = 0; c < columns.size(); c++) {
                if (columns.get(c).name().equals(pkColumns.get(i))) {
                    idx[i] = c;
                    break;
                }
            }
            if (idx[i] < 0) {
                throw new IOException("PK column '" + pkColumns.get(i)
                        + "' is not in the delta batch");
            }
        }
        return idx;
    }

    private static StructType sourceSchema(List<Column> columns) {
        StructType base = DeltaSchemas.structType(columns);
        StructField[] fields = new StructField[base.fields().length + 1];
        System.arraycopy(base.fields(), 0, fields, 0, base.fields().length);
        fields[fields.length - 1] = new StructField(OP, DataTypes.IntegerType, false, Metadata.empty());
        return new StructType(fields);
    }

    private static String mergeCondition(List<String> pkColumns) {
        List<String> parts = new ArrayList<>(pkColumns.size());
        for (String pk : pkColumns) {
            parts.add("t.`" + pk + "` = s.`" + pk + "`");
        }
        return String.join(" AND ", parts);
    }
}
