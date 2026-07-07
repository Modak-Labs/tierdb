package io.tierdb.lake.delta.commit;

import io.tierdb.common.RowBatchData.Column;
import java.util.List;
import org.apache.spark.sql.Row;

public record DeltaCommittable(List<Column> columns, List<Row> rows) {}
