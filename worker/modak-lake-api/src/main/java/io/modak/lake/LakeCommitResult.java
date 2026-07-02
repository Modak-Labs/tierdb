package io.modak.lake;

import io.modak.common.LakeSnapshotId;
import java.util.Map;

/**
 * Result of a cold-store commit. Distinguishes the <b>committed</b> snapshot from
 * the <b>readable</b> one (formats with deferred visibility may lag; Modak pins the
 * readable one). {@code publishProps} — what readers need to open this version,
 * e.g. {@code metadata_location} — merge into {@code lake_props} with the advance.
 */
public record LakeCommitResult(
        LakeSnapshotId committed,
        LakeSnapshotId readable,
        Map<String, String> publishProps) {

    public LakeCommitResult {
        publishProps = Map.copyOf(publishProps);
    }

    public static LakeCommitResult committedIsReadable(LakeSnapshotId s, Map<String, String> publishProps) {
        return new LakeCommitResult(s, s, publishProps);
    }
}
