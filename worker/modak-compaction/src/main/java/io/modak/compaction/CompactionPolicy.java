package io.modak.compaction;

import io.modak.common.DeltaBatch;
import io.modak.common.TableId;
import java.time.Instant;
import java.util.Optional;

/**
 * Strategy: when and what to fold. Tunes the eager/lazy dial — eager keeps the delta
 * (and cold-read merge cost) small but risks small files; lazy makes bigger files but
 * a larger delta. Empty result ⇒ nothing to compact this cycle.
 */
public interface CompactionPolicy {
    Optional<DeltaBatch> selectForCompaction(TableId table, Instant now);
}
