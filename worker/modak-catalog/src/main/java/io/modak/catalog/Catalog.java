package io.modak.catalog;

import io.modak.common.Cutline;
import io.modak.common.DeltaBatch;
import io.modak.common.LakeSnapshotId;
import io.modak.common.Lsn;
import io.modak.common.OpKind;
import io.modak.common.OpPhase;
import io.modak.common.PartitionBounds;
import io.modak.common.PartitionId;
import io.modak.common.PartitionState;
import io.modak.common.TableId;
import io.modak.common.TierKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade over the {@code modak.*} catalog tables, the cross-language contract
 * and the only coordination channel with the Rust extension. The
 * consistency-critical operations are single atomic transactions.
 */
public interface Catalog {

    TableId register(TableRegistration registration);

    List<StorageProfile> listStorageProfiles();

    Optional<StorageProfile> storageProfile(String name);

    StorageProfile defaultStorageProfile();

    void createStorageProfile(StorageProfile profile);

    boolean unregister(TableId table);

    Optional<RegisteredTable> lookup(String schemaName, String tableName);

    Optional<RegisteredTable> get(TableId table);

    List<RegisteredTable> listTables();

    void setMaintenancePolicy(TableId table, MaintenancePolicy policy);

    void requestMaintenance(TableId table, String requestedBy);

    boolean consumeMaintenanceRequest(TableId table);

    void initCutline(TableId table, TierKey t, LakeSnapshotId snapshot, String lakePropsJson);

    default void initCutline(TableId table, TierKey t, LakeSnapshotId snapshot) {
        initCutline(table, t, snapshot, null);
    }

    Cutline readCutline(TableId table);

    Optional<String> readLakeProps(TableId table);

    void advanceCutline(TableId table, TierKey newT, LakeSnapshotId snapshot);

    void advanceCutline(TableId table, TierKey newT, LakeSnapshotId snapshot,
            Map<String, String> lakePropsPatch);

    Optional<Lsn> readMirrorFrontier(TableId table);

    void advanceMirrorFrontier(TableId table, Lsn lsn, LakeSnapshotId snapshot,
            Map<String, String> lakePropsPatch);

    void advanceRetentionLine(TableId table, TierKey newT);

    void publishCompaction(TableId table, LakeSnapshotId snapshot, DeltaBatch folded,
            Map<String, String> lakePropsPatch);

    void publishRetention(TableId table, LakeSnapshotId snapshot, TierKey below,
            Map<String, String> lakePropsPatch);

    Optional<TierKey> readRetentionLine(TableId table);

    Cutline readHorizon(TableId table);

    Optional<Cutline> pinnedHorizon(TableId table);

    boolean beginLoad(TableId table, String label, LoadState state, String stagedFilesJson,
            String resultJson);

    Optional<LoadLabel> lookupLoad(TableId table, String label);

    List<LoadLabel> stagedLoads(TableId table);

    void finishLoad(TableId table, List<String> labels, LakeSnapshotId snapshot,
            Map<String, String> lakePropsPatch);

    void logOpPhase(UUID opId, TableId table, OpKind opKind, OpPhase phase,
            LakeSnapshotId snapshot, String detailsJson);

    List<TieringOp> findIncompleteOps(TableId table, OpKind opKind);

    void upsertPartition(PartitionId id, PartitionBounds bounds, PartitionState state);

    List<PartitionInfo> listPartitions(TableId table);

    void transition(PartitionId partition, PartitionState from, PartitionState to);
}
