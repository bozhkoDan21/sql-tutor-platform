package com.sqltrainer.util;

import com.sqltrainer.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

/**
 * Выполнение SQL-запросов с EXPLAIN ANALYZE.
 */
public class QueryExecutor {
    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    // Кеш результатов с TTL 30 секунд
    private static final Map<String, QueryResult> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000;

    // Планировщик для периодической очистки кеша
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Семафор для ограничения параллельных запросов
    private static final int MAX_CONCURRENT_QUERIES = Integer.parseInt(
            System.getenv().getOrDefault("MAX_CONCURRENT_QUERIES", "10"));
    private static final Semaphore querySemaphore = new Semaphore(MAX_CONCURRENT_QUERIES);

    // Таймаут ожидания в очереди (30 секунд)
    private static final int SEMAPHORE_TIMEOUT_SEC = Integer.parseInt(
            System.getenv().getOrDefault("SEMAPHORE_TIMEOUT_SEC", "30"));

    static {
        scheduler.scheduleAtFixedRate(() -> {
            int sizeBefore = cache.size();
            cache.entrySet().removeIf(entry ->
                    System.currentTimeMillis() - entry.getValue().getExecutionTimeMs() > CACHE_TTL_MS);
            int sizeAfter = cache.size();
            if (sizeBefore != sizeAfter) {
                log.debug("Cache cleanup: removed {} entries, remaining: {}", sizeBefore - sizeAfter, sizeAfter);
            }
        }, 1, 1, TimeUnit.MINUTES);

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

    /**
     * Результат выполнения запроса.
     */
    public static class QueryResult {
        private List<String> columns = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();
        private String explainJson;
        private String explainText;
        private long executionTimeMs;
        private String error;
        private boolean success;
        private int rowCount;
        private String signature;  // Добавлено поле для подписи

        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }

        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
            this.rowCount = rows.size();
        }

        public String getExplain() { return explainJson; }
        @Deprecated
        public void setExplain(String explain) { this.explainJson = explain; }

        public String getExplainJson() { return explainJson; }
        public void setExplainJson(String explainJson) { this.explainJson = explainJson; }

        public String getExplainText() { return explainText; }
        public void setExplainText(String explainText) { this.explainText = explainText; }

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

        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
    }

    /**
     * Выполнить запрос от имени студента.
     */
    public static QueryResult executeAsStudent(String dbName, String sql, boolean needExplain) {
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

            return executeAsStudentInternal(dbName, sql, needExplain);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResult("Запрос был прерван");
        } finally {
            if (acquired) {
                querySemaphore.release();
            }
        }
    }

    private static QueryResult executeAsStudentInternal(String dbName, String sql, boolean needExplain) {
        if (sql == null || sql.trim().isEmpty()) {
            return errorResult("Запрос пуст");
        }

        String sqlTrimmed = sql.trim();
        String[] statements = sqlTrimmed.split(";");

        String lastSelect = null;
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty() && trimmed.toLowerCase().startsWith("select")) {
                lastSelect = trimmed;
            }
        }

        if (lastSelect == null) {
            return errorResult("Не найден SELECT запрос. Разрешены только SELECT запросы.");
        }

        if (statements.length > 1 && log.isDebugEnabled()) {
            log.debug("Multiple statements detected, executing only the last SELECT: {}",
                    lastSelect.length() > 100 ? lastSelect.substring(0, 100) + "..." : lastSelect);
        }

        return executeSingleQuery(dbName, lastSelect, needExplain);
    }

    /**
     * Выполняет SELECT-запрос от имени студента.
     * Применяются ограничения: таймаут, максимальное количество строк из метаданных БД.
     *
     * @param dbName имя базы данных
     * @param sql SQL-запрос
     * @param needExplain флаг, нужно ли выполнять EXPLAIN ANALYZE
     * @return результат выполнения запроса
     */
    private static QueryResult executeSingleQuery(String dbName, String sql, boolean needExplain) {
        String sqlLower = sql.toLowerCase();

        if (containsDangerousPatterns(sqlLower)) {
            log.warn("Blocked potentially dangerous query: {}", sql);
            return errorResult("Запрос содержит запрещённые команды (DELETE, UPDATE, DROP и т.д.)");
        }

        // ПОЛУЧАЕМ maxRows ОДИН РАЗ
        int maxRows = DatabaseConfig.getMaxRowsForDatabase(dbName);

        // Ключ кэша ТЕПЕРЬ ВКЛЮЧАЕТ maxRows
        String cacheKey = dbName + ":" + sql + ":explain=" + needExplain + ":maxRows=" + maxRows;
        QueryResult cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.getExecutionTimeMs()) < CACHE_TTL_MS) {
            log.debug("Returning cached result for: {}", cacheKey);
            return cached;
        }

        long startTime = System.currentTimeMillis();
        QueryResult result = new QueryResult();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.STUDENT, dbName);
             Statement stmt = conn.createStatement()) {

            // Устанавливаем таймаут выполнения запроса (из переменных окружения)
            stmt.setQueryTimeout(DatabaseConfig.getQueryTimeout());

            // Устанавливаем лимит строк (maxRows УЖЕ получен выше)
            stmt.setMaxRows(maxRows);
            log.debug("Setting max_rows={} for database {}", maxRows, dbName);

            // Если нужно, получаем план выполнения запроса
            if (needExplain) {
                ExplainResult explainResult = getExplainPlan(stmt, sql);
                result.setExplainJson(explainResult.json);
                result.setExplainText(explainResult.text);
            }

            // Выполняем сам запрос
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

            // Генерируем подпись для верификации CSV
            String signature = generateSignature(sql, executionTime, result.getRowCount(), dbName);
            result.setSignature(signature);

            // Сохраняем в кэш на 30 секунд
            cache.put(cacheKey, result);

        } catch (SQLException e) {
            String errorMsg = handleSQLError(e);
            log.error("Query failed on database {}: {}", dbName, errorMsg);
            result.setError(errorMsg);
        }

        return result;
    }

    private static class ExplainResult {
        String json;
        String text;
        ExplainResult(String json, String text) {
            this.json = json;
            this.text = text;
        }
    }

    private static ExplainResult getExplainPlan(Statement stmt, String sql) throws SQLException {
        String explainSqlJson = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql;
        String explainSqlText = "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql;

        String jsonPlan = null;
        String textPlan = null;

        try {
            try (ResultSet rs = stmt.executeQuery(explainSqlJson)) {
                if (rs.next()) {
                    jsonPlan = rs.getString(1);
                }
            }
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug("JSON format not supported: {}", e.getMessage());
            }
        }

        try (ResultSet rs = stmt.executeQuery(explainSqlText)) {
            StringBuilder plan = new StringBuilder();
            while (rs.next()) {
                plan.append(rs.getString(1)).append("\n");
            }
            textPlan = plan.toString();
        }

        if (jsonPlan == null || !jsonPlan.trim().startsWith("[")) {
            jsonPlan = textPlan;
        }

        return new ExplainResult(jsonPlan, textPlan);
    }

    private static boolean containsDangerousPatterns(String sqlLower) {
        String[] dangerous = Constants.DANGEROUS_SQL_PATTERNS;
        for (String pattern : dangerous) {
            if (sqlLower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String handleSQLError(SQLException e) {
        String msg = e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("statement timeout")) {
            return "Превышен таймаут запроса (" + DatabaseConfig.getQueryTimeout() + " секунд)";
        }
        if (msg.contains("permission denied")) {
            return "Доступ запрещён (разрешены только SELECT запросы)";
        }
        if (msg.contains("syntax error")) {
            return "Синтаксическая ошибка SQL: " + e.getMessage();
        }
        if (msg.contains("multiple resultsets")) {
            return "Обнаружено несколько запросов. Будет выполнен только последний SELECT запрос.";
        }
        return e.getMessage();
    }

    private static QueryResult errorResult(String message) {
        QueryResult result = new QueryResult();
        result.setError(message);
        return result;
    }

    public static void clearCache() {
        int size = cache.size();
        cache.clear();
        log.info("Query cache cleared. Removed {} entries", size);
    }

    public static int getActiveQueryCount() {
        return MAX_CONCURRENT_QUERIES - querySemaphore.availablePermits();
    }

    public static int getMaxConcurrentQueries() {
        return MAX_CONCURRENT_QUERIES;
    }

    /**
     * Выполняет запрос от имени преподавателя.
     */
    public static QueryResult executeAsTeacher(String dbName, String sql, boolean needExplain) {
        if (sql == null || sql.trim().isEmpty()) {
            return errorResult("Запрос пуст");
        }

        long startTime = System.currentTimeMillis();
        QueryResult result = new QueryResult();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(30);

            if (needExplain) {
                ExplainResult explainResult = getExplainPlan(stmt, sql);
                result.setExplainJson(explainResult.json);
                result.setExplainText(explainResult.text);
            }

            boolean isSelect = sql.trim().toLowerCase().startsWith("select");

            if (isSelect) {
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
            } else {
                stmt.executeUpdate(sql);
                result.setSuccess(true);
                result.setColumns(new ArrayList<>());
                result.setRows(new ArrayList<>());
            }

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);

            // Генерируем подпись для результата
            String signature = generateSignature(sql, executionTime, result.getRowCount(), dbName);
            result.setSignature(signature);

            if (log.isDebugEnabled()) {
                log.debug("Teacher query executed in {} ms on database {}", executionTime, dbName);
            }

        } catch (SQLException e) {
            String errorMsg = handleSQLError(e);
            log.error("Teacher query failed on database {}: {}", dbName, errorMsg);
            result.setError(errorMsg);
        }

        return result;
    }

    /**
     * Генерирует подпись для CSV на основе данных запроса
     */
    public static String generateSignature(String query, long executionTimeMs, int rowCount, String dbName) {
        String data = query + executionTimeMs + rowCount + dbName + new Date().toString();
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, using simple hash");
            int hash = data.hashCode();
            return Integer.toHexString(Math.abs(hash)).substring(0, 8);
        }
    }
}