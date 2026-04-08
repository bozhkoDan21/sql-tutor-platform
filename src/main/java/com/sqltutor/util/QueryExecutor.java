package com.sqltutor.util;

import com.sqltutor.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Выполнение SQL-запросов с EXPLAIN ANALYZE.
 * ВАЖНО: EXPLAIN ANALYZE реально выполняет запрос, поэтому мы:
 * 1. Выполняем EXPLAIN ANALYZE для получения плана + времени
 * 2. Выполняем запрос ещё раз для получения данных
 * Это неизбежно, но мы кешируем результаты одинаковых запросов на короткое время.
 */
public class QueryExecutor {
    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    // Простой кеш результатов (TTL 30 секунд)
    private static final Map<String, QueryResult> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000;

    // Планировщик для периодической очистки кеша
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    static {
        // Запускаем периодическую очистку кеша каждую минуту
        scheduler.scheduleAtFixedRate(() -> {
            int sizeBefore = cache.size();
            cache.entrySet().removeIf(entry ->
                    System.currentTimeMillis() - entry.getValue().getExecutionTimeMs() > CACHE_TTL_MS);
            int sizeAfter = cache.size();
            if (sizeBefore != sizeAfter) {
                log.debug("Cache cleanup: removed {} entries, remaining: {}", sizeBefore - sizeAfter, sizeAfter);
            }
        }, 1, 1, TimeUnit.MINUTES);

        // Добавляем shutdown hook для корректного завершения планировщика
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    public static class QueryResult {
        private List<String> columns = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();
        private String explain;
        private long executionTimeMs;
        private String error;
        private boolean success;
        private int rowCount;

        // Геттеры и сеттеры
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }

        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
            this.rowCount = rows.size();
        }

        public String getExplain() { return explain; }
        public void setExplain(String explain) { this.explain = explain; }

        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

        public String getError() { return error; }
        public void setError(String error) {
            this.error = error;
            this.success = false;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getRowCount() { return rowCount; }
    }

    /**
     * Выполнить запрос от имени студента
     */
    public static QueryResult executeAsStudent(String dbName, String sql) {
        // Валидация
        if (sql == null || sql.trim().isEmpty()) {
            return errorResult("Query is empty");
        }

        String sqlTrimmed = sql.trim();
        String sqlLower = sqlTrimmed.toLowerCase();

        if (!sqlLower.startsWith("select")) {
            return errorResult("Only SELECT queries are allowed");
        }

        // Защита от опасных конструкций
        if (containsDangerousPatterns(sqlLower)) {
            log.warn("Blocked potentially dangerous query: {}", sql);
            return errorResult("Query contains prohibited patterns (subqueries with DELETE/UPDATE, etc.)");
        }

        // Проверяем кеш
        String cacheKey = dbName + ":" + sqlTrimmed;
        QueryResult cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.executionTimeMs) < CACHE_TTL_MS) {
            log.debug("Returning cached result for: {}", cacheKey);
            return cached;
        }

        long startTime = System.currentTimeMillis();
        QueryResult result = new QueryResult();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.STUDENT, dbName);
             Statement stmt = conn.createStatement()) {

            // Лимиты через JDBC (дополнительная защита)
            stmt.setQueryTimeout(DatabaseConfig.getQueryTimeout());
            stmt.setMaxRows(DatabaseConfig.getMaxRows());

            // Получаем план выполнения (EXPLAIN ANALYZE реально выполняет запрос)
            String explainPlan = getExplainPlan(stmt, sqlTrimmed);
            result.setExplain(explainPlan);

            // Получаем данные (запрос выполняется второй раз)
            ResultSet rs = stmt.executeQuery(sqlTrimmed);
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnName(i));
            }
            result.setColumns(columns);

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
            result.setRows(rows);
            result.setSuccess(true);

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);

            // Кешируем результат
            cache.put(cacheKey, result);

            log.info("Query executed: {} rows in {} ms on database {}",
                    rows.size(), executionTime, dbName);

        } catch (SQLException e) {
            String errorMsg = handleSQLError(e);
            log.error("Query failed on database {}: {}", dbName, errorMsg);
            result.setError(errorMsg);
        }

        return result;
    }

    /**
     * Получить план выполнения через EXPLAIN ANALYZE
     */
    private static String getExplainPlan(Statement stmt, String sql) throws SQLException {
        String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql;

        try (ResultSet rs = stmt.executeQuery(explainSql)) {
            StringBuilder plan = new StringBuilder();
            plan.append("=== EXPLAIN ANALYZE ===\n");
            while (rs.next()) {
                plan.append(rs.getString(1)).append("\n");
            }
            return plan.toString();
        }
    }

    /**
     * Проверка на опасные паттерны
     */
    private static boolean containsDangerousPatterns(String sqlLower) {
        String[] dangerous = {
                "delete", "update", "insert", "drop", "truncate", "alter",
                "create", "grant", "revoke", "pg_sleep", "benchmark"
        };

        for (String pattern : dangerous) {
            if (sqlLower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Обработка SQL исключений с понятными сообщениями
     */
    private static String handleSQLError(SQLException e) {
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("statement timeout")) {
            return "Query timeout exceeded (" + DatabaseConfig.getQueryTimeout() + " seconds)";
        }
        if (msg.contains("permission denied")) {
            return "Permission denied (only SELECT queries are allowed)";
        }
        if (msg.contains("syntax error")) {
            return "SQL syntax error: " + e.getMessage();
        }
        return e.getMessage();
    }

    private static QueryResult errorResult(String message) {
        QueryResult result = new QueryResult();
        result.setError(message);
        return result;
    }

    /**
     * Очистка кеша (для тестов или при изменении данных)
     */
    public static void clearCache() {
        cache.clear();
        log.info("Query cache cleared");
    }
}