package io.tierdb.lake.delta;

import io.tierdb.lake.LakeStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.Row;

final class DeltaStats {

    static final int SNAPSHOTS_WARN = 200;
    static final long SMALL_FILE_BYTES = 8L * 1024 * 1024;
    static final int SMALL_FILE_MIN_FILES = 16;

    private final DeltaTables tables;
    private final String path;

    DeltaStats(DeltaTables tables, String path) {
        this.tables = tables;
        this.path = path;
    }

    LakeStats collect() {
        if (!tables.exists(path)) {
            return LakeStats.EMPTY;
        }
        Row detail = tables.load(path).detail().collectAsList().get(0);
        long files = getLong(detail, "numFiles");
        long bytes = getLong(detail, "sizeInBytes");
        long snapshots = tables.load(path).history().count();

        Map<String, Double> values = new LinkedHashMap<>();
        values.put(LakeStats.FILES, (double) files);
        values.put(LakeStats.BYTES, (double) bytes);
        values.put(LakeStats.SNAPSHOTS, (double) snapshots);
        double avgFileBytes = files == 0 ? 0 : (double) bytes / files;
        values.put("avg_file_bytes", avgFileBytes);

        List<String> warnings = new ArrayList<>();
        if (files >= SMALL_FILE_MIN_FILES && avgFileBytes < SMALL_FILE_BYTES) {
            warnings.add(String.format(
                    "average data file is %.1f MB across %d files, small-file debt is slowing reads",
                    avgFileBytes / (1024 * 1024), files));
        }
        if (snapshots >= SNAPSHOTS_WARN) {
            warnings.add(snapshots + " versions in the transaction log, "
                    + "the log is due for expiry");
        }
        return new LakeStats(values, warnings);
    }

    private static long getLong(Row row, String field) {
        int idx = row.fieldIndex(field);
        return row.isNullAt(idx) ? 0 : row.getLong(idx);
    }
}
