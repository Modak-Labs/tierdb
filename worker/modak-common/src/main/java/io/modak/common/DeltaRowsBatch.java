package io.modak.common;

import io.modak.common.RowBatchData.Column;
import java.util.List;

/**
 * Fully-materialized delta entries with typed row values. Upserts carry the full
 * row image positional per {@link #columns()}; tombstones carry at least the pk
 * fields (the equality delete needs their typed values).
 */
public record DeltaRowsBatch(
        TableId table,
        List<String> pkColumns,
        List<Column> columns,
        List<Entry> entries) implements DeltaBatch {

    /** One delta entry; {@code pk} is the canonical {@link PkCodec} text. */
    public record Entry(String pk, boolean tombstone, long tierKey, long version, Object[] row) {}

    public DeltaRowsBatch {
        pkColumns = List.copyOf(pkColumns);
        columns = List.copyOf(columns);
        entries = List.copyOf(entries);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public List<Key> keys() {
        return entries.stream().map(e -> new Key(e.pk(), e.version())).toList();
    }
}
