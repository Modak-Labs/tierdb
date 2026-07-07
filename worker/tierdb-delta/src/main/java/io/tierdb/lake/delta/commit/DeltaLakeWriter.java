package io.tierdb.lake.delta.commit;

import io.tierdb.common.PartitionData;
import io.tierdb.common.RowBatchData;
import io.tierdb.common.RowBatchData.Column;
import io.tierdb.lake.delta.DeltaSchemas;
import io.tierdb.lake.commit.LakeWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;

final class DeltaLakeWriter implements LakeWriter<DeltaWriteResult> {

    private final List<Row> rows = new ArrayList<>();
    private List<Column> columns;

    @Override
    public void write(PartitionData data) throws IOException {
        if (!(data instanceof RowBatchData batch)) {
            throw new IOException("Delta writer expects RowBatchData, got "
                    + data.getClass().getName());
        }
        if (columns == null) {
            columns = batch.columns();
        }
        for (Object[] row : batch.rows()) {
            Object[] values = new Object[row.length];
            for (int i = 0; i < row.length; i++) {
                values[i] = DeltaSchemas.coerce(row[i]);
            }
            rows.add(RowFactory.create(values));
        }
    }

    @Override
    public DeltaWriteResult complete() {
        return new DeltaWriteResult(columns == null ? List.of() : columns, rows);
    }

    @Override
    public void close() {}
}
