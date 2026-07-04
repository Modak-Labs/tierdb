package io.modak.connector;

import java.util.List;

/** One captured seam, the registration row plus the cut-line the pin holds. */
public record SeamState(
        long tableId,
        List<String> primaryKeyCols,
        String tierKeyCol,
        String mode,
        String lakeFormat,
        String lakeTableRef,
        String metadataLocation,
        Long snapshotId,
        Long heapRetentionLag,
        long tierKeyHi,
        Long retentionLine,
        Long hybridSeam,
        Long pinId) {

    public boolean heapIsComplete() {
        return "mirrored".equals(mode) && heapRetentionLag == null;
    }

    public long readSeam() {
        return hybridSeam != null ? hybridSeam : tierKeyHi;
    }
}
