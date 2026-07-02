package io.modak.tiering;

import io.modak.catalog.RegisteredTable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/** The hot table's write frontier: {@code max(tier_key)}, or null when empty. */
public final class HotHighWater {

    public static Long query(DataSource dataSource, RegisteredTable meta) {
        String sql = "SELECT max(" + ident(meta.tierKeyCol()) + ") FROM "
                + ident(meta.schemaName()) + "." + ident(meta.tableName());
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            long v = rs.getLong(1);
            return rs.wasNull() ? null : v;
        } catch (SQLException e) {
            throw new TieringException("high-water probe failed for " + meta.tableName(), e);
        }
    }

    static String ident(String name) {
        return '"' + name.replace("\"", "\"\"") + '"';
    }

    private HotHighWater() {}
}
