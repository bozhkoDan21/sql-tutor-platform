package com.sqltrainer.servlet;

import com.google.gson.Gson;
import org.mindrot.jbcrypt.BCrypt;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Сервлет для проверки пароля доступа к базе данных.
 * При успешной проверке пароль сохраняется в сессии.
 */
@WebServlet("/api/database/verify")
public class DatabaseVerifyServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DatabaseVerifyServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> data = gson.fromJson(body, Map.class);

        String dbName = data.get("dbName");
        String password = data.get("password");

        Map<String, Object> response = new HashMap<>();

        if (dbName == null || dbName.isEmpty()) {
            response.put("success", false);
            response.put("error", "Database name is required");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        if (password == null || password.isEmpty()) {
            response.put("success", false);
            response.put("error", "Password is required");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // Проверяем пароль в таблице databases_metadata
        try (Connection conn = com.sqltrainer.config.DatabaseConfig.getConnection(
                com.sqltrainer.config.DatabaseConfig.Role.ADMIN, null)) {

            String sql = "SELECT access_password_hash FROM databases_metadata WHERE db_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, dbName);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String passwordHash = rs.getString("access_password_hash");

                    // Если пароль не установлен (NULL), доступ открыт
                    if (passwordHash == null || passwordHash.isEmpty()) {
                        response.put("success", true);
                        response.put("message", "Access granted (no password)");
                        saveAuthorizedDatabase(req, dbName);
                        resp.getWriter().write(gson.toJson(response));
                        return;
                    }

                    // Проверяем пароль
                    if (BCrypt.checkpw(password, passwordHash)) {
                        response.put("success", true);
                        response.put("message", "Access granted");
                        saveAuthorizedDatabase(req, dbName);
                        log.info("Database {} access granted", dbName);
                    } else {
                        response.put("success", false);
                        response.put("error", "Invalid password");
                        log.warn("Failed password attempt for database {}", dbName);
                    }
                } else {
                    response.put("success", false);
                    response.put("error", "Database not found");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to verify database password: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Database error: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    /**
     * Сохраняет информацию о том, что пользователь авторизован для доступа к базе.
     */
    private void saveAuthorizedDatabase(HttpServletRequest req, String dbName) {
        HttpSession session = req.getSession(true);
        @SuppressWarnings("unchecked")
        Set<String> authorizedDbs = (Set<String>) session.getAttribute("authorizedDatabases");
        if (authorizedDbs == null) {
            authorizedDbs = new HashSet<>();
            session.setAttribute("authorizedDatabases", authorizedDbs);
        }
        authorizedDbs.add(dbName);
        session.setMaxInactiveInterval(3600); // 1 час
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Проверка, авторизован ли пользователь для доступа к базе
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String dbName = req.getParameter("dbName");
        Map<String, Object> response = new HashMap<>();

        if (dbName == null || dbName.isEmpty()) {
            response.put("authorized", false);
            response.put("success", true);
        } else {
            HttpSession session = req.getSession(false);
            @SuppressWarnings("unchecked")
            Set<String> authorizedDbs = session != null ?
                    (Set<String>) session.getAttribute("authorizedDatabases") : null;
            response.put("authorized", authorizedDbs != null && authorizedDbs.contains(dbName));
            response.put("success", true);
        }

        resp.getWriter().write(gson.toJson(response));
    }
}