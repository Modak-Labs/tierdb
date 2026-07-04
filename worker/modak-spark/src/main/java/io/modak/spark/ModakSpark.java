package io.modak.spark;

import io.modak.connector.SeamClient;
import io.modak.connector.SeamOptions;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * Spark consumer of the Modak seam protocol (docs/reference/seam.md): pinned
 * two-tier reads and tier-routed writes over a Modak table.
 */
public final class ModakSpark {

    private ModakSpark() {}

    public static SeamRead read(SparkSession spark, SeamOptions options) {
        return new SeamRead(spark, options, SeamClient.capture(options, true));
    }

    public static void write(Dataset<Row> rows, SeamOptions options) {
        SeamWriter.write(rows, options);
    }

    public static void delete(Dataset<Row> keys, SeamOptions options) {
        SeamDeleter.delete(keys, options);
    }
}
