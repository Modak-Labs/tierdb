package io.tierdb.lake.delta;

import io.tierdb.common.RowBatchData.Column;
import io.tierdb.lake.ColdTableSpec;
import io.tierdb.lake.LakePartition;
import io.tierdb.lake.LakeSnapshotReader;
import io.tierdb.lake.LakeStorage;
import io.tierdb.lake.LakeTable;
import io.tierdb.lake.delta.commit.DeltaTieringFactory;
import io.tierdb.lake.commit.CommitterInitContext;
import io.tierdb.lake.commit.LakeTieringFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeltaLakeStorage implements LakeStorage {

    private final DeltaTables tables;

    public DeltaLakeStorage(Map<String, String> config) {
        this.tables = DeltaTables.from(Map.copyOf(config));
    }

    DeltaTables tables() {
        return tables;
    }

    @Override
    public String tableRef(String schema, String table) {
        return tables.tableRef(schema, table);
    }

    @Override
    public Map<String, String> createTableIfAbsent(String ref, List<Column> columns,
            Set<String> requiredCols, String tierKeyCol, LakePartition partition) {
        return DeltaTableBootstrap.createIfAbsent(
                tables, ref, columns, requiredCols, tierKeyCol, partition);
    }

    @Override
    public void dropTable(String ref) {
        tables.drop(ref);
    }

    @Override
    public LakeTieringFactory<?, ?> tieringFactory() {
        return new DeltaTieringFactory(tables);
    }

    @Override
    public LakeSnapshotReader snapshotReader() {
        throw new UnsupportedOperationException(
                "Delta snapshot reader is not implemented; reads go through the extension");
    }

    @Override
    public LakeTable table(CommitterInitContext ctx, ColdTableSpec spec) {
        return new DeltaLakeTable(tables, ctx.lakeTableRef(), spec);
    }
}
