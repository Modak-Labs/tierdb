package io.tierdb.lake.delta;

import io.tierdb.common.RowBatchData.Column;
import java.util.List;
import org.apache.spark.sql.types.StructType;

public final class DeltaSchemaEvolution {

    private final DeltaTables tables;
    private final String path;

    public DeltaSchemaEvolution(DeltaTables tables, String path) {
        this.tables = tables;
        this.path = path;
    }

    public boolean addMissing(List<Column> columns) {
        StructType schema = tables.load(path).toDF().schema();
        boolean any = false;
        StringBuilder adds = new StringBuilder();
        for (Column col : columns) {
            if (hasField(schema, col.name())) {
                continue;
            }
            if (any) {
                adds.append(", ");
            }
            adds.append('`').append(col.name()).append("` ").append(sqlType(col));
            any = true;
        }
        if (any) {
            tables.spark().sql("ALTER TABLE delta.`" + path + "` ADD COLUMNS (" + adds + ")");
        }
        return any;
    }

    private static boolean hasField(StructType schema, String name) {
        for (String f : schema.fieldNames()) {
            if (f.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String sqlType(Column col) {
        return switch (col.type()) {
            case LONG -> "bigint";
            case DOUBLE -> "double";
            case BOOLEAN -> "boolean";
            case TEXT, UUID -> "string";
            case TIMESTAMP -> "timestamp";
            case DATE -> "date";
            case DECIMAL -> "decimal(" + col.precision() + "," + col.scale() + ")";
            case BINARY -> "binary";
        };
    }
}
