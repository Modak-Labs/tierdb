package io.tierdb.lake.delta;

import io.delta.kernel.Snapshot;
import io.delta.kernel.Table;
import io.delta.kernel.defaults.engine.DefaultEngine;
import io.delta.kernel.engine.Engine;
import io.delta.kernel.exceptions.TableNotFoundException;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public final class DeltaTables {

    private final Engine engine;
    private final Configuration hadoopConf;
    private final Map<String, String> config;

    private DeltaTables(Engine engine, Configuration hadoopConf, Map<String, String> config) {
        this.engine = engine;
        this.hadoopConf = hadoopConf;
        this.config = config;
    }

    static DeltaTables from(Map<String, String> config) {
        Configuration conf = DeltaConfig.hadoopConf(config);
        return new DeltaTables(DefaultEngine.create(conf), conf, config);
    }

    public Engine engine() {
        return engine;
    }

    public Configuration hadoopConf() {
        return hadoopConf;
    }

    public String warehouse() {
        String warehouse = config.get("warehouse");
        if (warehouse == null || warehouse.isBlank()) {
            throw new IllegalStateException("lake config has no 'warehouse'");
        }
        return warehouse.replaceAll("/+$", "");
    }

    public String tableRef(String schema, String table) {
        return warehouse() + "/" + schema + "." + table;
    }

    public boolean exists(String path) {
        try {
            Table.forPath(engine, path).getLatestSnapshot(engine);
            return true;
        } catch (TableNotFoundException e) {
            return false;
        }
    }

    public Table load(String path) {
        return Table.forPath(engine, path);
    }

    public Snapshot latestSnapshot(String path) {
        return Table.forPath(engine, path).getLatestSnapshot(engine);
    }

    public long latestVersion(String path) {
        try {
            return Table.forPath(engine, path).getLatestSnapshot(engine).getVersion();
        } catch (TableNotFoundException e) {
            return -1L;
        }
    }

    public FileSystem fileSystem(String path) {
        try {
            return new Path(path).getFileSystem(hadoopConf);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to open filesystem for " + path, e);
        }
    }

    public void drop(String path) {
        try {
            Path p = new Path(path);
            FileSystem fs = p.getFileSystem(hadoopConf);
            if (fs.exists(p)) {
                fs.delete(p, true);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to drop Delta table " + path, e);
        }
    }
}
