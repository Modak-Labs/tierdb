package io.modak.worker.ops;

import io.modak.lake.LakeTable;
import io.modak.lake.maintain.MaintenanceEngine;
import io.modak.lake.maintain.MaintenancePlan;
import io.modak.lake.maintain.MaintenanceResult;
import java.util.Map;

/** The in-worker engine: executes the plan in process via the format plugin. */
public final class EmbeddedMaintenanceEngine implements MaintenanceEngine {

    @Override
    public MaintenanceResult run(LakeTable table, MaintenancePlan plan,
            Map<String, String> snapshotProps) {
        return table.maintain(plan, snapshotProps);
    }
}
