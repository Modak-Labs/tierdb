package io.modak.common;

/**
 * A value along a table's immutable tier-key (e.g. epoch micros). Defines data
 * temperature and aging order; a row never changes its tier-key, so a PK stays on
 * exactly one side of the cut-line.
 */
public record TierKey(long value) implements Comparable<TierKey> {
    @Override
    public int compareTo(TierKey o) {
        return Long.compare(this.value, o.value);
    }
}
