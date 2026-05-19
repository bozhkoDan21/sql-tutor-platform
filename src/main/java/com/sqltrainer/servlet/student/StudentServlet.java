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

import static com.sqltrainer.util.Constants.*;

/**
 * Сервлет для выполнения SQL-запросов.
 * Студенты могут выполнять только SELECT запросы с ограничениями.
 * Преподаватели могут выполнять любые SQL команды.
 *
 * Мониторинг сессий студентов УДАЛЁН по требованию.
 */
@WebServlet("/api/execute")
public class StudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(StudentServlet.class);
    private final Gson gson = new Gson();

    /**
     * Получает IP-адрес клиента для логирования.
     *
     * @param req HTTP-запрос
     * @return IP-адрес клиента
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
     *
     * @param req HTTP-запрос
     * @param dbName имя базы данных
     * @return true если пользователь авторизован для доступа к этой базе
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
     *
     * @param dbName имя базы данных
     * @return true если база защищена паролем
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

        // Получение параметров запроса
        String dbName = req.getParameter("database");
        String query = req.getParameter("query");
        String explainParam = req.getParameter("explain");
        boolean needExplain = explainParam == null || "true".equals(explainParam);

        // Валидация: имя базы данных обязательно
        if (dbName == null || dbName.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Не указано имя базы данных\"}");
            return;
        }

        // Валидация: SQL запрос не может быть пустым
        if (query == null || query.isEmpty()) {
            resp.getWriter().write("{\"error\":\"Не указан SQL запрос\"}");
            return;
        }

        // Валидация: максимальная длина запроса
        if (query.length() > Constants.MAX_QUERY_LENGTH) {
            resp.getWriter().write("{\"error\":\"Запрос слишком длинный (максимум 10000 символов)\"}");
            return;
        }

        String ipAddress = getClientIp(req);

        // Проверка, авторизован ли пользователь как преподаватель
        // Преподаватель определяется по наличию атрибута "authenticated" в сессии
        boolean isTeacher = false;
        HttpSession teacherSession = req.getSession(false);
        if (teacherSession != null && teacherSession.getAttribute("authenticated") != null) {
            Boolean auth = (Boolean) teacherSession.getAttribute("authenticated");
            isTeacher = auth != null && auth;
        }

        // Проверка прав доступа к базе данных
        if (!isDatabaseAccessible(dbName, isTeacher)) {
            log.warn("User from IP {} attempted to access unauthorized database: {}", ipAddress, dbName);
            resp.getWriter().write("{\"error\":\"Доступ к базе данных запрещён: " + dbName + "\"}");
            return;
        }

        // Для студентов: дополнительная проверка пароля (если база защищена)
        if (!isTeacher && isDatabasePasswordProtected(dbName) && !isAuthorizedForDatabase(req, dbName)) {
            log.warn("User from IP {} attempted to access password-protected database without auth: {}", ipAddress, dbName);
            resp.getWriter().write("{\"error\":\"Требуется пароль для доступа к базе: " + dbName + "\"}");
            return;
        }

        // Для студентов: проверка, что запрос начинается с SELECT
        // INSERT, UPDATE, DELETE, DROP и другие команды запрещены
        if (!isTeacher && !query.trim().toLowerCase().startsWith("select")) {
            log.warn("Student from IP {} attempted to execute non-SELECT query: {}", ipAddress, query);
            resp.getWriter().write("{\"error\":\"Для студентов разрешены только SELECT запросы\"}");
            return;
        }

        // Для преподавателя: логируем выполнение запроса (без сохранения сессии)
        if (isTeacher) {
            String shortQuery = query.length() > 200 ? query.substring(0, 200) + "..." : query;
            log.info("Teacher executing query on {}: {}", dbName, shortQuery);
        }

        // Выполнение запроса в зависимости от роли
        QueryExecutor.QueryResult result;
        if (isTeacher) {
            result = QueryExecutor.executeAsTeacher(dbName, query, needExplain);
        } else {
            result = QueryExecutor.executeAsStudent(dbName, query, needExplain);
        }

        // Отправка результата клиенту
        resp.getWriter().write(gson.toJson(result));
    }

    /**
     * Проверяет, имеет ли пользователь доступ к указанной базе данных.
     *
     * Правила доступа:
     * - Преподаватели имеют доступ ко всем базам данных
     * - Студенты имеют доступ только к базам, которые:
     *   1. Существуют в таблице databases_metadata
     *   2. Имеют флаг is_visible = true
     *   3. Находятся в периоде доступа (access_start <= сегодня <= access_end)
     *
     * @param dbName имя базы данных
     * @param isTeacher флаг, является ли пользователь преподавателем
     * @return true если доступ разрешён
     */
    private boolean isDatabaseAccessible(String dbName, boolean isTeacher) {
        // Преподаватель имеет доступ ко всем базам
        if (isTeacher) {
            return true;
        }

        // Проверяем для студента в таблице databases_metadata
        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT is_visible, access_start, access_end FROM databases_metadata WHERE db_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, dbName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    // Проверка видимости базы
                    if (!rs.getBoolean("is_visible")) {
                        log.debug("Database {} is not visible to students", dbName);
                        return false;
                    }

                    // Проверка периода доступа
                    Date accessStart = rs.getDate("access_start");
                    Date accessEnd = rs.getDate("access_end");
                    Date now = new Date();

                    if (accessStart != null && now.before(accessStart)) {
                        log.debug("Database {} access starts after {}", dbName, accessStart);
                        return false;
                    }
                    if (accessEnd != null && now.after(accessEnd)) {
                        log.debug("Database {} access ended at {}", dbName, accessEnd);
                        return false;
                    }

                    return true;
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to check database access for {}: {}", dbName, e.getMessage());
        }

        // Если база не найдена в метаданных, доступ запрещён
        return false;
    }
}