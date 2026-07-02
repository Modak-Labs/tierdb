package io.modak.compaction;

import io.modak.catalog.Catalog;
import io.modak.catalog.RegisteredTable;
import io.modak.catalog.TieringOp;
import io.modak.common.DeltaBatch;
import io.modak.common.TableId;
import io.modak.lake.CommitterInitContext;
import io.modak.lake.LakeCommitResult;
import io.modak.lake.LakeStorage;
import io.modak.lake.LakeTieringProps;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One cycle: select a delta batch, fold it into the cold base as one snapshot,
 * advance {@code S} and clear the folded rows atomically. Read-pins block the
 * cycle (a pinned reader merges live delta over its older snapshot). Folding is
 * idempotent, so a crashed op is abandoned and simply re-folds next cycle.
 */
public final class CompactionWorker {

    private final Catalog catalog;
    private final LakeStorage lake;
    private final CompactionPolicy policy;

    public CompactionWorker(Catalog catalog, LakeStorage lake, CompactionPolicy policy) {
        this.catalog = Objects.requireNonNull(catalog);
        this.lake = Objects.requireNonNull(lake);
        this.policy = Objects.requireNonNull(policy);
    }

    public void runCycle(TableId table, Instant now) throws IOException {
        abandonStaleOps(table);

        if (catalog.pinnedHorizon(table).isPresent()) {
            return;
        }
        Optional<DeltaBatch> maybeBatch = policy.selectForCompaction(table, now);
        if (maybeBatch.isEmpty()) {
            return;
        }
        DeltaBatch batch = maybeBatch.get();
        RegisteredTable meta = catalog.get(table)
                .orElseThrow(() -> new IllegalStateException("table not registered: " + table));

        UUID opId = UUID.randomUUID();
        catalog.logOpPhase(opId, table, TieringOp.KIND_COMPACTION, TieringOp.PHASE_FLUSHING,
                null, "{\"entries\":" + batch.size() + "}");

        LakeCommitResult result = lake
                .mergeWriter(new CommitterInitContext(table, meta.lakeTableRef()))
                .applyDelta(batch, snapshotProps(table, opId));

        catalog.logOpPhase(opId, table, TieringOp.KIND_COMPACTION, TieringOp.PHASE_COMMITTED,
                result.readable(), null);

        catalog.publishCompaction(table, result.readable(), batch, result.publishProps());
        catalog.logOpPhase(opId, table, TieringOp.KIND_COMPACTION, TieringOp.PHASE_ADVANCED,
                null, null);
    }

    private void abandonStaleOps(TableId table) {
        for (TieringOp op : catalog.findIncompleteOps(table, TieringOp.KIND_COMPACTION)) {
            catalog.logOpPhase(op.opId(), table, TieringOp.KIND_COMPACTION,
                    TieringOp.PHASE_ABANDONED, null, null);
        }
    }

    static Map<String, String> snapshotProps(TableId table, UUID opId) {
        Map<String, String> props = new HashMap<>();
        props.put(LakeTieringProps.OP_ID, opId.toString());
        props.put(LakeTieringProps.OP_KIND, LakeTieringProps.OP_KIND_COMPACTION);
        props.put(LakeTieringProps.TABLE_ID, Long.toString(table.oid()));
        props.put(LakeTieringProps.COMMIT_USER, LakeTieringProps.COMMIT_USER_COMPACTION);
        return props;
    }
}
