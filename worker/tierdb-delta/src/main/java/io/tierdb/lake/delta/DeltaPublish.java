package io.tierdb.lake.delta;

import java.util.Map;

public final class DeltaPublish {

    static final String TABLE_LOCATION = "table_location";

    private DeltaPublish() {}

    public static Map<String, String> props(String path) {
        return Map.of(TABLE_LOCATION, path);
    }
}
