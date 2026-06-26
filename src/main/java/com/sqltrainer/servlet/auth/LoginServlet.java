package com.sqltrainer.servlet.auth;

import com.google.gson.Gson;
import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.util.CsrfTokenManager;
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
import java.util.Map;

@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> loginData = gson.fromJson(body, Map.class);
        String password = loginData.get("password");

        Map<String, Object> response = new HashMap<>();

        if (password == null || password.isEmpty()) {
            response.put("success", false);
            response.put("error", "Требуется пароль");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String storedHash = getTeacherPasswordHash(conn);

            if (storedHash != null && BCrypt.checkpw(password, storedHash)) {
                HttpSession session = req.getSession(true);
                session.setAttribute("authenticated", true);
                session.setMaxInactiveInterval(3600);

                String csrfToken = CsrfTokenManager.generateToken(session);

                response.put("success", true);
                response.put("message", "Вход выполнен успешно");
                response.put("csrfToken", csrfToken);
                log.info("Teacher logged in successfully");
            } else {
                response.put("success", false);
                response.put("error", "Неверный пароль");
                log.warn("Failed login attempt");
            }

        } catch (SQLException e) {
            log.error("Database error during login", e);
            response.put("success", false);
            response.put("error", "Ошибка базы данных: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", session != null && session.getAttribute("authenticated") != null);
        response.put("success", true);

        resp.getWriter().write(gson.toJson(response));
    }

    private String getTeacherPasswordHash(Connection conn) throws SQLException {
        String sql = "SELECT setting_value FROM teacher_settings WHERE setting_key = 'password'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("setting_value");
            }
            // Создаём хеш для пароля по умолчанию
            createDefaultPasswordHash(conn);
            return BCrypt.hashpw("teacher123", BCrypt.gensalt());
        }
    }

    private void createDefaultPasswordHash(Connection conn) throws SQLException {
        String hashedPassword = BCrypt.hashpw("teacher123", BCrypt.gensalt());
        String sql = "INSERT INTO teacher_settings (setting_key, setting_value) VALUES ('password', ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hashedPassword);
            stmt.executeUpdate();
            log.info("Created default teacher password hash");
        }
    }
}