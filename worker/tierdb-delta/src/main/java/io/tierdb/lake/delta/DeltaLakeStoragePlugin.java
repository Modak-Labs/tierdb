package io.tierdb.lake.delta;

import io.tierdb.lake.LakeStorage;
import io.tierdb.lake.LakeStoragePlugin;
import java.util.Map;

public final class DeltaLakeStoragePlugin implements LakeStoragePlugin {

    public static final String IDENTIFIER = "delta";

    @Override
    public String identifier() {
        return IDENTIFIER;
    }

    @Override
    public LakeStorage create(Map<String, String> config) {
        return new DeltaLakeStorage(config);
    }
}
