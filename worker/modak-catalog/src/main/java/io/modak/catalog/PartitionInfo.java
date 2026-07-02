package io.modak.catalog;

import io.modak.common.PartitionBounds;
import io.modak.common.PartitionId;
import io.modak.common.PartitionState;

/** A row of {@code modak.partitions} as read back. */
public record PartitionInfo(PartitionId id, PartitionBounds bounds, PartitionState state) {}
