package io.tierdb.lake.delta;

import io.delta.tables.DeltaColumnBuilder;
import io.delta.tables.DeltaTable;
import io.delta.tables.DeltaTableBuilder;
import io.tierdb.common.RowBatchData.Column;
import io.tierdb.lake.LakePartition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.spark.sql.SparkSession;

final class DeltaTableBootstrap {

    private DeltaTableBootstrap() {}

    static Map<String, String> createIfAbsent(DeltaTables tables, String path,
            List<Column> columns, Set<String> requiredCols, String tierKeyCol,
            LakePartition partition) {
        if (tables.exists(path)) {
            return DeltaPublish.props(path);
        }
        SparkSession spark = tables.spark();
        DeltaTableBuilder builder = DeltaTable.createIfNotExists(spark).location(path);
        for (Column col : columns) {
            DeltaColumnBuilder column = DeltaTable.columnBuilder(spark, col.name())
                    .dataType(DeltaSchemas.sparkType(col))
                    .nullable(!requiredCols.contains(col.name()));
            builder.addColumn(column.build());
        }
        builder.execute();
        return DeltaPublish.props(path);
    }
}
