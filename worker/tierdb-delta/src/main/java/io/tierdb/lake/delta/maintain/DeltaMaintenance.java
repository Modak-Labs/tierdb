package io.tierdb.lake.delta.maintain;

import io.delta.tables.DeltaTable;
import io.tierdb.lake.delta.DeltaCommit;
import io.tierdb.lake.delta.DeltaTables;
import io.tierdb.lake.maintain.MaintenancePlan;
import io.tierdb.lake.maintain.MaintenanceResult;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.Row;

public final class DeltaMaintenance {

    private record Knobs(
            boolean optimizeEnabled,
            boolean vacuumEnabled,
            long vacuumRetentionHours) {

        static Knobs from(Map<String, String> settings) {
            return new Knobs(
                    boolOf(settings, "optimize_enabled", true),
                    boolOf(settings, "vacuum_enabled", true),
                    longOf(settings, "vacuum_retention_hours", 168));
        }

        private static long longOf(Map<String, String> settings, String key, long fallback) {
            String v = settings.get(key);
            return v == null ? fallback : Long.parseLong(v);
        }

        private static boolean boolOf(Map<String, String> settings, String key, boolean fallback) {
            String v = settings.get(key);
            return v == null ? fallback : Boolean.parseBoolean(v);
        }
    }

    private final DeltaTables tables;
    private final String path;

    public DeltaMaintenance(DeltaTables tables, String path) {
        this.tables = tables;
        this.path = path;
    }

    public MaintenanceResult run(MaintenancePlan plan, Map<String, String> snapshotProps) {
        if (!tables.exists(path)) {
            return MaintenanceResult.NOOP;
        }
        Knobs knobs = Knobs.from(plan.settings());
        Map<String, Long> counters = new LinkedHashMap<>();
        if (knobs.optimizeEnabled()) {
            counters.put("optimize_removed_files", optimize(snapshotProps));
        }
        if (knobs.vacuumEnabled()) {
            counters.put("vacuumed", vacuum(knobs, plan.pinnedSnapshotFloor()) ? 1L : 0L);
        }
        return new MaintenanceResult(counters);
    }

    private long optimize(Map<String, String> snapshotProps) {
        long before = numFiles();
        DeltaCommit.withUserMetadata(tables.spark(), snapshotProps, () ->
                tables.spark().sql("OPTIMIZE delta.`" + path + "`").collect());
        long after = numFiles();
        return Math.max(0, before - after);
    }

    private boolean vacuum(Knobs knobs, long pinnedSnapshotFloor) {
        double retentionHours = Math.max(knobs.vacuumRetentionHours(),
                retentionForFloor(pinnedSnapshotFloor));
        DeltaTable.forPath(tables.spark(), path).vacuum(retentionHours);
        return true;
    }

    private double retentionForFloor(long pinnedSnapshotFloor) {
        if (pinnedSnapshotFloor <= 0) {
            return 0;
        }
        List<Row> rows = tables.load(path).history()
                .select("version", "timestamp")
                .collectAsList();
        Timestamp floorTs = null;
        for (Row r : rows) {
            if (r.getLong(0) == pinnedSnapshotFloor && !r.isNullAt(1)) {
                floorTs = r.getTimestamp(1);
                break;
            }
        }
        if (floorTs == null) {
            return 0;
        }
        long ageMillis = System.currentTimeMillis() - floorTs.getTime();
        return Math.ceil(ageMillis / 3_600_000.0) + 1;
    }

    private long numFiles() {
        Row detail = tables.load(path).detail().collectAsList().get(0);
        int idx = detail.fieldIndex("numFiles");
        return detail.isNullAt(idx) ? 0 : detail.getLong(idx);
    }
}
