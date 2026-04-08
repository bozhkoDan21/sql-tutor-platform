package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import com.sqltutor.util.QueryExecutor;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@WebServlet("/api/execute")
public class StudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(StudentServlet.class);
    private final Gson gson = new Gson();

    // Активные сессии студентов (для мониторинга преподавателем)
    private static final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    // Ограничение частоты запросов (защита от DoS)
    private static final ConcurrentHashMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private static final long MIN_REQUEST_INTERVAL_MS = 1000; // минимум 1 секунда между запросами

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

        // Геттеры используются TeacherServlet через рефлексию Gson
        public String getSessionId() { return sessionId; }
        public String getDbName() { return dbName; }
        public String getLastQuery() { return lastQuery; }
        public Date getLastAccess() { return lastAccess; }
    }

    /**
     * Получить копию активных сессий (для TeacherServlet)
     */
    public static ConcurrentHashMap<String, SessionInfo> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String dbName = req.getParameter("database");
        String query = req.getParameter("query");

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

        // Получаем или создаем сессию
        HttpSession session = req.getSession(true);
        String sessionId = session.getId();

        // Ограничение частоты запросов (защита от DoS)
        Long lastRequest = lastRequestTime.get(sessionId);
        if (lastRequest != null && System.currentTimeMillis() - lastRequest < MIN_REQUEST_INTERVAL_MS) {
            log.warn("Rate limit exceeded for session {}", sessionId);
            resp.getWriter().write("{\"error\":\"Too many requests. Please wait before sending another query.\"}");
            return;
        }
        lastRequestTime.put(sessionId, System.currentTimeMillis());

        // Периодическая очистка старых записей частоты (раз в 100 запросов)
        if (lastRequestTime.size() > 1000) {
            cleanOldRateLimits();
        }

        // Сохраняем информацию об активности
        activeSessions.put(sessionId, new SessionInfo(sessionId, dbName, query));

        // Очищаем старые сессии (старше 30 минут)
        cleanOldSessions();

        log.info("Executing query on {} from session {}: {}", dbName, sessionId,
                query.length() > 100 ? query.substring(0, 100) + "..." : query);

        // Выполняем запрос
        QueryExecutor.QueryResult result = QueryExecutor.executeAsStudent(dbName, query);

        resp.getWriter().write(gson.toJson(result));
    }

    /**
     * Очистка неактивных сессий (старше 30 минут)
     */
    private void cleanOldSessions() {
        long cutoff = System.currentTimeMillis() - 30 * 60 * 1000;
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().lastAccess.getTime() < cutoff);
    }

    /**
     * Очистка старых записей ограничения частоты запросов
     */
    private void cleanOldRateLimits() {
        long cutoff = System.currentTimeMillis() - 60 * 1000; // старше минуты
        lastRequestTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}