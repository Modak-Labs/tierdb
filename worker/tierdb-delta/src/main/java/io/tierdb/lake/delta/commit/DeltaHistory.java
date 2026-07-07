package io.tierdb.lake.delta.commit;

import io.tierdb.lake.delta.DeltaJson;
import io.tierdb.lake.delta.DeltaTables;
import io.tierdb.lake.commit.LakeTieringProps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.spark.sql.Row;

final class DeltaHistory {

    record Commit(long version, Map<String, String> props) {}

    private DeltaHistory() {}

    static Optional<Commit> latestOp(DeltaTables tables, String path, String opKind, String opId) {
        List<Row> rows = tables.load(path).history()
                .select("version", "userMetadata")
                .collectAsList();
        Commit best = null;
        for (Row r : rows) {
            long version = r.getLong(0);
            String userMetadata = r.isNullAt(1) ? null : r.getString(1);
            Map<String, String> props = DeltaJson.read(userMetadata);
            if (!opKind.equals(props.get(LakeTieringProps.OP_KIND))) {
                continue;
            }
            String stamped = props.get(LakeTieringProps.OP_ID);
            boolean match = opId == null ? stamped != null : opId.equals(stamped);
            if (match && (best == null || version > best.version())) {
                best = new Commit(version, props);
            }
        }
        return Optional.ofNullable(best);
    }
}
