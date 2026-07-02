package io.modak.catalog;

import io.modak.common.TableId;
import java.util.List;
import java.util.Optional;

/**
 * A row of {@code modak.tables} as read back. {@code publicationName} /
 * {@code slotName} are set only for {@link TableMode#MIRRORED} tables;
 * {@code retentionLag} is empty when the heap keeps everything.
 */
public record RegisteredTable(
        TableId id,
        String schemaName,
        String tableName,
        List<String> primaryKeyCols,
        String tierKeyCol,
        String partitionScheme,
        String lakeFormat,
        String lakeTableRef,
        String lakeProps,
        int schemaVersion,
        TableMode mode,
        String publicationName,
        String slotName,
        Optional<Long> retentionLag) {

    /** Tiered-mode row — the shape that existed before table modes. */
    public RegisteredTable(
            TableId id,
            String schemaName,
            String tableName,
            List<String> primaryKeyCols,
            String tierKeyCol,
            String partitionScheme,
            String lakeFormat,
            String lakeTableRef,
            String lakeProps,
            int schemaVersion) {
        this(id, schemaName, tableName, primaryKeyCols, tierKeyCol, partitionScheme,
                lakeFormat, lakeTableRef, lakeProps, schemaVersion,
                TableMode.TIERED, null, null, Optional.empty());
    }

    /** Mirrored table that also drops heap partitions below the retention line. */
    public boolean dropsHeapPartitions() {
        return mode == TableMode.TIERED || retentionLag.isPresent();
    }
}
