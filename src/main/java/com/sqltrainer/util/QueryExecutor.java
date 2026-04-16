package com.sqltrainer.util;

import com.sqltrainer.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

/**
 * Выполнение SQL-запросов с EXPLAIN ANALYZE.
 * <p>
 * Поддерживает:
 * <ul>
 *   <li>Одиночные SELECT запросы</li>
 *   <li>Несколько SELECT запросов через ; (выполняется последний)</li>
 *   <li>Кеширование результатов на 30 секунд</li>
 *   <li>Ограничение параллельных запросов через семафор</li>
 * </ul>
 * </p>
 */
public class QueryExecutor {
    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    /** Кеш результатов (TTL 30 секунд) */
    private static final Map<String, QueryResult> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000;

    /** Планировщик для периодической очистки кеша */
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** Семафор для ограничения параллельных запросов */
    private static final int MAX_CONCURRENT_QUERIES = Integer.parseInt(
            System.getenv().getOrDefault("MAX_CONCURRENT_QUERIES", "10"));
    private static final Semaphore querySemaphore = new Semaphore(MAX_CONCURRENT_QUERIES);

    /** Таймаут ожидания в очереди (30 секунд) */
    private static final int SEMAPHORE_TIMEOUT_SEC = Integer.parseInt(
            System.getenv().getOrDefault("SEMAPHORE_TIMEOUT_SEC", "30"));

    static {
        // Периодическая очистка кеша каждую минуту
        scheduler.scheduleAtFixedRate(() -> {
            int sizeBefore = cache.size();
            cache.entrySet().removeIf(entry ->
                    System.currentTimeMillis() - entry.getValue().getExecutionTimeMs() > CACHE_TTL_MS);
            int sizeAfter = cache.size();
            if (sizeBefore != sizeAfter) {
                log.debug("Cache cleanup: removed {} entries, remaining: {}", sizeBefore - sizeAfter, sizeAfter);
            }
        }, 1, 1, TimeUnit.MINUTES);

        // Shutdown hook для планировщика
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

        log.info("QueryExecutor initialized with max concurrent queries: {}", MAX_CONCURRENT_QUERIES);
    }

    public static class QueryResult {
        private List<String> columns = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();
        private String explain;
        private long executionTimeMs;
        private String error;
        private boolean success;
        private int rowCount;

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
     * Выполнить запрос от имени студента с ограничением параллельности.
     * Поддерживает несколько SELECT запросов, разделённых точкой с запятой.
     * Возвращает результат последнего SELECT запроса.
     *
     * @param dbName имя базы данных
     * @param sql    SQL запрос (может содержать несколько SELECT через ;)
     * @return результат выполнения
     */
    public static QueryResult executeAsStudent(String dbName, String sql) {
        // Пытаемся получить разрешение от семафора
        boolean acquired = false;
        try {
            acquired = querySemaphore.tryAcquire(SEMAPHORE_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!acquired) {
                int activeQueries = MAX_CONCURRENT_QUERIES - querySemaphore.availablePermits();
                log.warn("Query rejected - too many concurrent queries. Active: {}, Max: {}",
                        activeQueries, MAX_CONCURRENT_QUERIES);
                return errorResult("Сервер перегружен. Пожалуйста, повторите запрос позже. " +
                        "Активных запросов: " + activeQueries);
            }

            // Выполняем сам запрос
            return executeAsStudentInternal(dbName, sql);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResult("Query was interrupted");
        } finally {
            if (acquired) {
                querySemaphore.release();
            }
        }
    }

    /**
     * Внутренний метод выполнения запроса (без семафора)
     */
    private static QueryResult executeAsStudentInternal(String dbName, String sql) {
        // Валидация
        if (sql == null || sql.trim().isEmpty()) {
            return errorResult("Query is empty");
        }

        String sqlTrimmed = sql.trim();

        // Разбиваем запрос на отдельные statements
        String[] statements = sqlTrimmed.split(";");

        // Ищем последний SELECT запрос (игнорируем пустые и не-SELECT)
        String lastSelect = null;
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty() && trimmed.toLowerCase().startsWith("select")) {
                lastSelect = trimmed;
            }
        }

        if (lastSelect == null) {
            return errorResult("No SELECT query found. Only SELECT queries are allowed.");
        }

        // Если был только один запрос или несколько, но последний SELECT
        if (log.isDebugEnabled()) {
            log.debug("Multiple statements detected, executing only the last SELECT: {}",
                    lastSelect.length() > 100 ? lastSelect.substring(0, 100) + "..." : lastSelect);
        }

        // Выполняем последний SELECT запрос
        return executeSingleQuery(dbName, lastSelect);
    }

    /**
     * Выполняет один SELECT запрос
     */
    private static QueryResult executeSingleQuery(String dbName, String sql) {
        String sqlLower = sql.toLowerCase();

        // Защита от опасных конструкций
        if (containsDangerousPatterns(sqlLower)) {
            log.warn("Blocked potentially dangerous query: {}", sql);
            return errorResult("Query contains prohibited patterns (DELETE, UPDATE, DROP, etc.)");
        }

        // Проверяем кеш
        String cacheKey = dbName + ":" + sql;
        QueryResult cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.getExecutionTimeMs()) < CACHE_TTL_MS) {
            log.debug("Returning cached result for: {}", cacheKey);
            return cached;
        }

        long startTime = System.currentTimeMillis();
        QueryResult result = new QueryResult();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.STUDENT, dbName);
             Statement stmt = conn.createStatement()) {

            // Лимиты через JDBC
            stmt.setQueryTimeout(DatabaseConfig.getQueryTimeout());
            stmt.setMaxRows(DatabaseConfig.getMaxRows());

            // Получаем план выполнения
            String explainPlan = getExplainPlan(stmt, sql);
            result.setExplain(explainPlan);

            // Получаем данные
            try (ResultSet rs = stmt.executeQuery(sql)) {
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
            }

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);

            // Кешируем результат
            cache.put(cacheKey, result);

            if (log.isDebugEnabled()) {
                log.debug("Query executed: {} rows in {} ms on database {}",
                        result.getRowCount(), executionTime, dbName);
            }

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
        if (msg.contains("multiple resultsets")) {
            return "Multiple queries detected. Only the last SELECT query will be executed.";
        }
        return e.getMessage();
    }

    private static QueryResult errorResult(String message) {
        QueryResult result = new QueryResult();
        result.setError(message);
        return result;
    }

    /**
     * Очистка кеша (при изменении данных или создании/удалении БД)
     */
    public static void clearCache() {
        int size = cache.size();
        cache.clear();
        log.info("Query cache cleared. Removed {} entries", size);
    }

    /**
     * Получить количество активных запросов
     */
    public static int getActiveQueryCount() {
        return MAX_CONCURRENT_QUERIES - querySemaphore.availablePermits();
    }

    /**
     * Получить максимальное количество параллельных запросов
     */
    public static int getMaxConcurrentQueries() {
        return MAX_CONCURRENT_QUERIES;
    }
}