package com.sqltrainer.servlet.student;

import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.util.Constants;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.sqltrainer.util.Constants.*;

/**
 * Сервлет для выполнения SQL-запросов студентами.
 * Поддерживает только SELECT запросы, ограничивает время выполнения и количество строк.
 */
@WebServlet("/api/execute")
public class StudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(StudentServlet.class);
    private final Gson gson = new Gson();

    // Активные сессии студентов для мониторинга преподавателем
    private static final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    // Ограничение частоты запросов для защиты от DoS
    private static final ConcurrentHashMap<Long, Long> lastRequestTime = new ConcurrentHashMap<>();

    // Статистика запросов
    private static int queryCounter = 0;
    private static long lastLogTime = System.currentTimeMillis();

    // Планировщик для очистки заблокированных сессий
    private static final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    // Список баз данных, доступных студентам
    private static final Set<String> ALLOWED_DATABASES = new HashSet<>(Arrays.asList(
            Constants.ALLOWED_STUDENT_DATABASES
    ));

    static {
        // Запускаем очистку заблокированных сессий каждую минуту
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                long blockedThreshold = now - 5 * 60 * 1000; // 5 минут

                int removed = 0;
                var iterator = activeSessions.values().iterator();
                while (iterator.hasNext()) {
                    SessionInfo info = iterator.next();
                    // Удаляем заблокированные сессии старше 5 минут
                    if (info.isBlocked() && info.getLastAccess().getTime() < blockedThreshold) {
                        iterator.remove();
                        removed++;
                    }
                }
                if (removed > 0) {
                    log.info("Scheduled cleanup: removed {} old blocked sessions", removed);
                }
            } catch (Exception e) {
                log.warn("Cleanup error: {}", e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Информация о сессии студента для преподавателя.
     */
    public static class SessionInfo {
        private final String sessionId;
        private final String login;
        private final String lastQuery;
        private final Date lastAccess;
        private final String dbName;
        private final long lastQueryTimeMs;
        private boolean blocked;

        public SessionInfo(String sessionId, String login, String dbName, String lastQuery, long lastQueryTimeMs) {
            this.sessionId = sessionId;
            this.login = login;
            this.dbName = dbName;
            this.lastQuery = lastQuery;
            this.lastAccess = new Date();
            this.lastQueryTimeMs = lastQueryTimeMs;
            this.blocked = false;
        }

        public String getSessionId() { return sessionId; }
        public String getLogin() { return login; }
        public String getDbName() { return dbName; }
        public String getLastQuery() { return lastQuery; }
        public Date getLastAccess() { return lastAccess; }
        public long getLastQueryTimeMs() { return lastQueryTimeMs; }
        public boolean isBlocked() { return blocked; }
        public void setBlocked(boolean blocked) { this.blocked = blocked; }
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

        if (query.length() > Constants.MAX_QUERY_LENGTH) {
            resp.getWriter().write("{\"error\":\"Query too long (max 10000 characters)\"}");
            return;
        }

        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) {
            resp.getWriter().write("{\"error\":\"User not authenticated\"}");
            return;
        }

        HttpSession session = req.getSession(true);
        String sessionId = session.getId();
        String login = (String) req.getAttribute("login");

        // Проверка, не заблокирована ли текущая сессия
        SessionInfo existing = activeSessions.get(sessionId);
        if (existing != null && existing.isBlocked()) {
            log.warn("Blocked session {} attempted to execute query for user {}", sessionId, login);
            resp.getWriter().write("{\"error\":\"Your session has been terminated by teacher. Please re-login.\"}");
            return;
        }

        // Проверка доступа к базе данных
        if (!isDatabaseAccessibleToStudent(dbName, userId)) {
            log.warn("User {} attempted to access unauthorized database: {}", userId, dbName);
            resp.getWriter().write("{\"error\":\"Access denied to database: " + dbName + "\"}");
            return;
        }

        // Rate limiting
        Long lastRequest = lastRequestTime.get(userId);
        if (lastRequest != null && System.currentTimeMillis() - lastRequest < MIN_REQUEST_INTERVAL_MS) {
            log.warn("Rate limit exceeded for user {}", userId);
            resp.getWriter().write("{\"error\":\"Too many requests. Please wait before sending another query.\"}");
            return;
        }
        lastRequestTime.put(userId, System.currentTimeMillis());

        if (lastRequestTime.size() > 1000) {
            cleanOldRateLimits();
        }

        if (log.isDebugEnabled()) {
            log.debug("Executing query on {} from user {}: {}", dbName, userId,
                    query.length() > 100 ? query.substring(0, 100) + "..." : query);
        }

        QueryExecutor.QueryResult result = QueryExecutor.executeAsStudent(dbName, query, needExplain);
        long executionTime = result.getExecutionTimeMs();

        // Обновляем сессию после выполнения запроса
        activeSessions.put(sessionId, new SessionInfo(sessionId, login, dbName, query, executionTime));
        cleanOldSessions();

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
     * Проверяет, имеет ли студент доступ к указанной базе данных.
     * Сначала проверяет по списку разрешённых баз, затем через права PostgreSQL.
     */
    private boolean isDatabaseAccessibleToStudent(String dbName, Long userId) {
        if (ALLOWED_DATABASES.contains(dbName)) {
            return true;
        }

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

    /**
     * Удаляет сессии старше 30 минут.
     */
    private void cleanOldSessions() {
        long cutoff = System.currentTimeMillis() - Constants.SESSION_TTL_MS;
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().lastAccess.getTime() < cutoff);
    }

    /**
     * Удаляет записи rate limiting старше 1 минуты.
     */
    private void cleanOldRateLimits() {
        long cutoff = System.currentTimeMillis() - Constants.RATE_LIMIT_TTL_MS;
        lastRequestTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}