package io.tierdb.lake.delta;

import java.util.Map;
import org.apache.hadoop.conf.Configuration;

public final class DeltaConfig {

    private DeltaConfig() {}

    public static Configuration hadoopConf(Map<String, String> config) {
        Configuration conf = new Configuration();
        for (Map.Entry<String, String> e : config.entrySet()) {
            if (e.getKey().startsWith("hadoop.")) {
                conf.set(e.getKey().substring("hadoop.".length()), e.getValue());
            }
        }
        if (config.containsKey("s3.endpoint") || config.containsKey("s3.access-key")) {
            conf.set("fs.s3.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
            setIfPresent(conf, config, "s3.endpoint", "fs.s3a.endpoint");
            setIfPresent(conf, config, "s3.access-key", "fs.s3a.access.key");
            setIfPresent(conf, config, "s3.secret-key", "fs.s3a.secret.key");
            conf.set("fs.s3a.endpoint.region", config.getOrDefault("s3.region", "us-east-1"));
            conf.set("fs.s3a.path.style.access",
                    config.getOrDefault("s3.path-style-access", "true"));
            conf.set("fs.s3a.connection.ssl.enabled",
                    config.getOrDefault("s3.ssl-enabled", "false"));
        }
        return conf;
    }

    private static void setIfPresent(Configuration conf, Map<String, String> config,
            String key, String hadoopKey) {
        String v = config.get(key);
        if (v != null) {
            conf.set(hadoopKey, v);
        }
    }
}
