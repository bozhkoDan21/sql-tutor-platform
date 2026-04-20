package com.sqltrainer.servlet;

import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.util.QueryExecutor;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@WebServlet("/api/execute")
public class StudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(StudentServlet.class);
    private final Gson gson = new Gson();

    // Активные сессии студентов (для мониторинга преподавателем)
    private static final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    // Ограничение частоты запросов (защита от DoS) - по userId
    private static final ConcurrentHashMap<Long, Long> lastRequestTime = new ConcurrentHashMap<>();
    private static final long MIN_REQUEST_INTERVAL_MS = 1000; // минимум 1 секунда между запросами

    // Агрегированная статистика запросов
    private static int queryCounter = 0;
    private static long lastLogTime = System.currentTimeMillis();

    // ИСПРАВЛЕНО: список разрешённых для студентов баз данных
    private static final Set<String> ALLOWED_DATABASES = new HashSet<>(Arrays.asList(
            "sql_tutor_university_db",
            "archaeology_10m"
    ));

    public static class SessionInfo {
        private final String sessionId;
        private final String lastQuery;
        private final Date lastAccess;
        private final String dbName;

        public SessionInfo(String sessionId, String dbName, String lastQuery) {
            this.sessionId = sessionId;
            this.dbName = dbName;
            this.lastQuery = lastQuery;
            this.lastAccess = new Date();
        }

        public String getSessionId() { return sessionId; }
        public String getDbName() { return dbName; }
        public String getLastQuery() { return lastQuery; }
        public Date getLastAccess() { return lastAccess; }
    }

    public static ConcurrentHashMap<String, SessionInfo> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String dbName = req.getParameter("database");
        String query = req.getParameter("query");
        String explainParam = req.getParameter("explain");
        boolean needExplain = explainParam == null || "true".equals(explainParam);

        if (dbName == null || dbName.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Database name is required\"}");
            return;
        }

        if (query == null || query.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Query is required\"}");
            return;
        }

        if (query.length() > 10000) {
            resp.getWriter().write("{\"error\":\"Query too long (max 10000 characters)\"}");
            return;
        }

        // Получаем userId из атрибута (установлен в JwtAuthFilter)
        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) {
            resp.getWriter().write("{\"error\":\"User not authenticated\"}");
            return;
        }

        // ИСПРАВЛЕНО: проверка доступа к базе данных
        if (!isDatabaseAccessibleToStudent(dbName, userId)) {
            log.warn("User {} attempted to access unauthorized database: {}", userId, dbName);
            resp.getWriter().write("{\"error\":\"Access denied to database: " + dbName + "\"}");
            return;
        }

        HttpSession session = req.getSession(true);
        String sessionId = session.getId();

        // Rate limiting по userId
        Long lastRequest = lastRequestTime.get(userId);
        if (lastRequest != null && System.currentTimeMillis() - lastRequest < MIN_REQUEST_INTERVAL_MS) {
            log.warn("Rate limit exceeded for user {}", userId);
            resp.getWriter().write("{\"error\":\"Too many requests. Please wait before sending another query.\"}");
            return;
        }
        lastRequestTime.put(userId, System.currentTimeMillis());

        // Периодическая очистка старых записей частоты
        if (lastRequestTime.size() > 1000) {
            cleanOldRateLimits();
        }

        activeSessions.put(sessionId, new SessionInfo(sessionId, dbName, query));
        cleanOldSessions();

        if (log.isDebugEnabled()) {
            log.debug("Executing query on {} from user {}: {}", dbName, userId,
                    query.length() > 100 ? query.substring(0, 100) + "..." : query);
        }

        QueryExecutor.QueryResult result = QueryExecutor.executeAsStudent(dbName, query, needExplain);
        resp.getWriter().write(gson.toJson(result));

        queryCounter++;
        if (queryCounter >= 100 || System.currentTimeMillis() - lastLogTime > 60000) {
            log.info("Processed {} queries in last period. Active sessions: {}, Rate limit entries: {}",
                    queryCounter, activeSessions.size(), lastRequestTime.size());
            queryCounter = 0;
            lastLogTime = System.currentTimeMillis();
        }
    }

    /**
     * ИСПРАВЛЕНО: проверка доступа студента к базе данных
     */
    private boolean isDatabaseAccessibleToStudent(String dbName, Long userId) {
        // Проверка по списку разрешённых баз
        if (ALLOWED_DATABASES.contains(dbName)) {
            return true;
        }

        // Дополнительная проверка через права PostgreSQL
        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT has_database_privilege($1, $2, 'CONNECT')";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "students");
                stmt.setString(2, dbName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getBoolean(1)) {
                    log.debug("User {} granted access to database {} via PostgreSQL privileges", userId, dbName);
                    return true;
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to check database access for {}: {}", dbName, e.getMessage());
        }

        log.warn("User {} denied access to database {}", userId, dbName);
        return false;
    }

    private void cleanOldSessions() {
        long cutoff = System.currentTimeMillis() - 30 * 60 * 1000;
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().lastAccess.getTime() < cutoff);
    }

    private void cleanOldRateLimits() {
        long cutoff = System.currentTimeMillis() - 60 * 1000; // старше минуты
        lastRequestTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}