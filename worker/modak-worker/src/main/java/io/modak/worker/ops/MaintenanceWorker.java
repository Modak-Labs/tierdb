package io.modak.worker.ops;

import io.modak.catalog.Catalog;
import io.modak.catalog.LoadLabel;
import io.modak.catalog.RegisteredTable;
import io.modak.common.OpKind;
import io.modak.common.OpPhase;
import io.modak.lake.ColdTableSpec;
import io.modak.lake.commit.CommitterInitContext;
import io.modak.lake.LakeStorage;
import io.modak.lake.commit.LakeTieringProps;
import io.modak.lake.maintain.MaintenanceEngine;
import io.modak.lake.maintain.MaintenancePlan;
import io.modak.lake.maintain.MaintenanceResult;
import io.modak.load.StagedFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

/** The maintenance loop for one table: resolves the policy, computes the pinned floor, and hands the plan to the format's {@link MaintenanceEngine}. */
public final class MaintenanceWorker {

    private final Catalog catalog;
    private final LakeStorage lake;
    private final MaintenanceEngine engine;
    private final Map<String, String> defaultSettings;

    public MaintenanceWorker(Catalog catalog, LakeStorage lake, MaintenanceEngine engine,
            Map<String, String> defaultSettings) {
        this.catalog = Objects.requireNonNull(catalog);
        this.lake = Objects.requireNonNull(lake);
        this.engine = Objects.requireNonNull(engine);
        this.defaultSettings = Map.copyOf(defaultSettings);
    }

    public MaintenanceResult runCycle(RegisteredTable table, boolean force) {
        MaintenancePlan plan = buildPlan(table);
        if (!force && "false".equals(plan.settings().get("maintenance_enabled"))) {
            return MaintenanceResult.NOOP;
        }
        UUID opId = UUID.randomUUID();
        MaintenanceResult result = engine.run(
                lake.table(new CommitterInitContext(table.id(), table.lakeTableRef()),
                        new ColdTableSpec(table.primaryKeyCols(), table.tierKeyCol())),
                plan,
                LakeTieringProps.snapshotProps(opId, OpKind.MAINTENANCE, table.id()));
        if (force || !result.isNoop()) {
            catalog.logOpPhase(opId, table.id(), OpKind.MAINTENANCE,
                    OpPhase.ADVANCED, null, countersJson(result));
        }
        return result;
    }

    public MaintenancePlan buildPlan(RegisteredTable table) {
        return new MaintenancePlan(
                table.maintenancePolicy().resolve(defaultSettings),
                catalog.readHorizon(table.id()).snapshot().id(),
                stagedFilePaths(table));
    }

    private List<String> stagedFilePaths(RegisteredTable table) {
        List<String> paths = new ArrayList<>();
        for (LoadLabel label : catalog.stagedLoads(table.id())) {
            StagedFiles.fromJson(label.stagedFilesJson())
                    .ifPresent(staged -> paths.addAll(staged.files()));
        }
        return paths;
    }

    private static String countersJson(MaintenanceResult result) {
        StringJoiner json = new StringJoiner(",", "{", "}");
        result.counters().forEach((key, value) -> json.add("\"" + key + "\":" + value));
        return json.toString();
    }
}
