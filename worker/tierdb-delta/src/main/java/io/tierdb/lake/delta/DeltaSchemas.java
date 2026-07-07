package io.tierdb.lake.delta;

import io.tierdb.common.RowBatchData.Column;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

public final class DeltaSchemas {

    private DeltaSchemas() {}

    public static DataType sparkType(Column col) {
        return switch (col.type()) {
            case LONG -> DataTypes.LongType;
            case DOUBLE -> DataTypes.DoubleType;
            case BOOLEAN -> DataTypes.BooleanType;
            case TEXT, UUID -> DataTypes.StringType;
            case TIMESTAMP -> DataTypes.TimestampType;
            case DATE -> DataTypes.DateType;
            case DECIMAL -> DataTypes.createDecimalType(col.precision(), col.scale());
            case BINARY -> DataTypes.BinaryType;
        };
    }

    public static StructType structType(List<Column> columns) {
        StructField[] fields = new StructField[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            fields[i] = new StructField(col.name(), sparkType(col), true, org.apache.spark.sql.types.Metadata.empty());
        }
        return new StructType(fields);
    }

    public static Object coerce(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return Timestamp.from(odt.toInstant());
        }
        if (value instanceof LocalDate ld) {
            return Date.valueOf(ld);
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        return value;
    }
}
