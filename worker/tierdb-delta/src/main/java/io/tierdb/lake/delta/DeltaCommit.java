package io.tierdb.lake.delta;

import java.util.Map;
import org.apache.spark.sql.SparkSession;

public final class DeltaCommit {

    private static final String USER_METADATA_KEY = "spark.databricks.delta.commitInfo.userMetadata";

    private DeltaCommit() {}

    public static void withUserMetadata(SparkSession spark, Map<String, String> props, Runnable op) {
        String previous = spark.conf().getOption(USER_METADATA_KEY).getOrElse(() -> null);
        spark.conf().set(USER_METADATA_KEY, DeltaJson.write(props));
        try {
            op.run();
        } finally {
            if (previous == null) {
                spark.conf().unset(USER_METADATA_KEY);
            } else {
                spark.conf().set(USER_METADATA_KEY, previous);
            }
        }
    }
}
