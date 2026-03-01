package com.sqltutor.util;

import com.sqltutor.config.DatabaseConfig;
import java.sql.*;
import java.util.*;

public class QueryExecutor {

    public static class QueryResult {
        private List<String> columns = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();
        private String explain;
        private long executionTime;
        private String error;
        private boolean success;

        // геттеры
        public List<String> getColumns() { return columns; }
        public List<Map<String, Object>> getRows() { return rows; }
        public String getExplain() { return explain; }
        public long getExecutionTime() { return executionTime; }
        public String getError() { return error; }
        public boolean isSuccess() { return success; }

        // сеттеры
        public void setColumns(List<String> columns) { this.columns = columns; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
        public void setExplain(String explain) { this.explain = explain; }
        public void setExecutionTime(long time) { this.executionTime = time; }
        public void setError(String error) {
            this.error = error;
            this.success = false;
        }
        public void setSuccess(boolean success) { this.success = success; }
    }

    public static QueryResult executeAsStudent(String dbName, String sql) {
        QueryResult result = new QueryResult();
        long startTime = System.currentTimeMillis();

        // Валидация
        if (sql == null || sql.trim().isEmpty()) {
            result.setError("Query is empty");
            return result;
        }

        String sqlLower = sql.trim().toLowerCase();
        if (!sqlLower.startsWith("select")) {
            result.setError("Only SELECT queries are allowed");
            return result;
        }

        try (Connection conn = DatabaseConfig.getConnection(
                DatabaseConfig.Role.STUDENT, dbName, null);
             Statement stmt = conn.createStatement()) {

            // Дополнительная защита
            stmt.setMaxRows(1000);

            // EXPLAIN
            try (ResultSet rs = stmt.executeQuery("EXPLAIN (ANALYZE) " + sql)) {
                StringBuilder exp = new StringBuilder();
                while (rs.next()) {
                    exp.append(rs.getString(1)).append("\n");
                }
                result.setExplain(exp.toString());
            } catch (SQLException e) {
                result.setExplain("EXPLAIN failed: " + e.getMessage());
            }

            // Данные
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();

                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= cols; i++) {
                    columns.add(meta.getColumnName(i));
                }
                result.setColumns(columns);

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                result.setRows(rows);
                result.setSuccess(true);
            }

        } catch (SQLException e) {
            if (e.getMessage().contains("timeout")) {
                result.setError("Query timeout (max 30 seconds)");
            } else {
                result.setError(e.getMessage());
            }
        }

        result.setExecutionTime(System.currentTimeMillis() - startTime);
        return result;
    }
}