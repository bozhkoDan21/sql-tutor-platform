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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.sqltrainer.util.Constants.*;

/**
 * Сервлет для выполнения SQL-запросов.
 * Студенты могут выполнять только SELECT запросы с ограничениями.
 * Преподаватели могут выполнять любые SQL команды.
 */
@WebServlet("/api/execute")
public class StudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(StudentServlet.class);
    private final Gson gson = new Gson();

    // Активные сессии студентов для мониторинга преподавателем
    private static final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    // Планировщик для очистки заблокированных сессий
    private static final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

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
     * Информация о сессии для преподавателя.
     */
    public static class SessionInfo {
        private final String sessionId;
        private final String ipAddress;
        private final String lastQuery;
        private final Date lastAccess;
        private final String dbName;
        private final long lastQueryTimeMs;
        private boolean blocked;
        private final boolean isTeacher;

        public SessionInfo(String sessionId, String ipAddress, String dbName, String lastQuery, long lastQueryTimeMs, boolean isTeacher) {
            this.sessionId = sessionId;
            this.ipAddress = ipAddress;
            this.dbName = dbName;
            this.lastQuery = lastQuery;
            this.lastAccess = new Date();
            this.lastQueryTimeMs = lastQueryTimeMs;
            this.blocked = false;
            this.isTeacher = isTeacher;
        }

        public String getSessionId() { return sessionId; }
        public String getIpAddress() { return ipAddress; }
        public String getDbName() { return dbName; }
        public String getLastQuery() { return lastQuery; }
        public Date getLastAccess() { return lastAccess; }
        public long getLastQueryTimeMs() { return lastQueryTimeMs; }
        public boolean isBlocked() { return blocked; }
        public void setBlocked(boolean blocked) { this.blocked = blocked; }
        public boolean isTeacher() { return isTeacher; }
    }

    public static ConcurrentHashMap<String, SessionInfo> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }

    /**
     * Получает IP-адрес клиента.
     */
    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        // Если localhost, получаем реальный IP
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                ip = "127.0.0.1";
            }
        }
        return ip;
    }

    /**
     * Проверяет, авторизован ли пользователь для доступа к защищённой паролем базе.
     */
    private boolean isAuthorizedForDatabase(HttpServletRequest req, String dbName) {
        HttpSession session = req.getSession(false);
        if (session == null) return false;
        @SuppressWarnings("unchecked")
        Set<String> authorizedDbs = (Set<String>) session.getAttribute("authorizedDatabases");
        return authorizedDbs != null && authorizedDbs.contains(dbName);
    }

    /**
     * Проверяет, защищена ли база данных паролем.
     */
    private boolean isDatabasePasswordProtected(String dbName) {
        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT access_password_hash FROM databases_metadata WHERE db_name = ? AND access_password_hash IS NOT NULL";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, dbName);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            log.warn("Failed to check if database is password protected: {}", e.getMessage());
            return false;
        }
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
            resp.getWriter().write("{\"error\":\"Не указано имя базы данных\"}");
            return;
        }

        if (query == null || query.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Не указан SQL запрос\"}");
            return;
        }

        if (query.length() > Constants.MAX_QUERY_LENGTH) {
            resp.getWriter().write("{\"error\":\"Запрос слишком длинный (максимум 10000 символов)\"}");
            return;
        }

        HttpSession session = req.getSession(true);
        String sessionId = session.getId();
        String ipAddress = getClientIp(req);

        // Проверка, авторизован ли пользователь как преподаватель
        boolean isTeacher = false;
        HttpSession teacherSession = req.getSession(false);
        if (teacherSession != null && teacherSession.getAttribute("authenticated") != null) {
            Boolean auth = (Boolean) teacherSession.getAttribute("authenticated");
            isTeacher = auth != null && auth;
        }

        // Проверка, не заблокирована ли текущая сессия (только для студентов)
        if (!isTeacher) {
            SessionInfo existing = activeSessions.get(sessionId);
            if (existing != null && existing.isBlocked()) {
                log.warn("Blocked session {} attempted to execute query from IP {}", sessionId, ipAddress);
                resp.getWriter().write("{\"error\":\"Ваша сессия была завершена преподавателем. Пожалуйста, войдите снова.\"}");
                return;
            }
        }

        // Проверка прав доступа к базе данных
        if (!isDatabaseAccessible(dbName, isTeacher)) {
            log.warn("User from IP {} attempted to access unauthorized database: {}", ipAddress, dbName);
            resp.getWriter().write("{\"error\":\"Доступ к базе данных запрещён: " + dbName + "\"}");
            return;
        }

        // Для студентов: дополнительная проверка пароля
        if (!isTeacher && isDatabasePasswordProtected(dbName) && !isAuthorizedForDatabase(req, dbName)) {
            log.warn("User from IP {} attempted to access password-protected database without auth: {}", ipAddress, dbName);
            resp.getWriter().write("{\"error\":\"Требуется пароль для доступа к базе: " + dbName + "\"}");
            return;
        }

        // Для студентов: проверка, что запрос начинается с SELECT
        if (!isTeacher && !query.trim().toLowerCase().startsWith("select")) {
            log.warn("Student from IP {} attempted to execute non-SELECT query: {}", ipAddress, query);
            resp.getWriter().write("{\"error\":\"Для студентов разрешены только SELECT запросы\"}");
            return;
        }

        // Для преподавателя: логируем, что выполняется запрос с полными правами
        if (isTeacher) {
            log.info("Teacher executing query on {}: {}", dbName, query.length() > 200 ? query.substring(0, 200) + "..." : query);
        }

        // Выполнение запроса в зависимости от роли
        QueryExecutor.QueryResult result;
        if (isTeacher) {
            result = QueryExecutor.executeAsTeacher(dbName, query, needExplain);
        } else {
            result = QueryExecutor.executeAsStudent(dbName, query, needExplain);
        }

        long executionTime = result.getExecutionTimeMs();

        // Обновляем сессию после выполнения запроса
        activeSessions.put(sessionId, new SessionInfo(sessionId, ipAddress, dbName, query, executionTime, isTeacher));
        cleanOldSessions();

        resp.getWriter().write(gson.toJson(result));
    }

    /**
     * Проверяет, имеет ли пользователь доступ к указанной базе данных.
     * Преподаватели имеют доступ ко всем базам.
     * Студенты - только к базам, которые есть в metadata с is_visible = true
     * и соответствующие периоду доступа.
     */
    private boolean isDatabaseAccessible(String dbName, boolean isTeacher) {
        if (isTeacher) {
            return true;
        }

        // Проверяем в таблице databases_metadata
        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT is_visible, access_start, access_end FROM databases_metadata WHERE db_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, dbName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    if (!rs.getBoolean("is_visible")) {
                        return false;
                    }
                    // Проверка периода доступа
                    Date accessStart = rs.getDate("access_start");
                    Date accessEnd = rs.getDate("access_end");
                    Date now = new Date();
                    if (accessStart != null && now.before(accessStart)) {
                        return false;
                    }
                    if (accessEnd != null && now.after(accessEnd)) {
                        return false;
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to check database access for {}: {}", dbName, e.getMessage());
        }

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
}