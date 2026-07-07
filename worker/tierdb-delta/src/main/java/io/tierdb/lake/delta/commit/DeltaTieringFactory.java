package io.tierdb.lake.delta.commit;

import io.tierdb.lake.delta.DeltaTables;
import io.tierdb.lake.commit.CommitterInitContext;
import io.tierdb.lake.commit.LakeCommitter;
import io.tierdb.lake.commit.LakeTieringFactory;
import io.tierdb.lake.commit.LakeWriter;
import io.tierdb.lake.commit.WriterInitContext;
import java.io.IOException;

public final class DeltaTieringFactory
        implements LakeTieringFactory<DeltaWriteResult, DeltaCommittable> {

    private final DeltaTables tables;

    public DeltaTieringFactory(DeltaTables tables) {
        this.tables = tables;
    }

    @Override
    public LakeWriter<DeltaWriteResult> createWriter(WriterInitContext ctx) throws IOException {
        return new DeltaLakeWriter();
    }

    @Override
    public LakeCommitter<DeltaWriteResult, DeltaCommittable> createCommitter(
            CommitterInitContext ctx) throws IOException {
        return new DeltaLakeCommitter(tables, ctx.lakeTableRef());
    }
}
