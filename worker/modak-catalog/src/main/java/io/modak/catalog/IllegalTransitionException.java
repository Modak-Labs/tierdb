package io.modak.catalog;

import io.modak.common.PartitionId;
import io.modak.common.PartitionState;

public class IllegalTransitionException extends CatalogException {
    public IllegalTransitionException(PartitionId id, PartitionState from, PartitionState to) {
        super("illegal partition transition for " + id + ": " + from + " -> " + to);
    }
}
