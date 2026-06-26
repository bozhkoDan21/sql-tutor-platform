package com.sqltrainer.servlet.teacher;

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

@WebServlet("/api/teacher/change-password")
public class ChangePasswordServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> response = new HashMap<>();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("authenticated") == null) {
            response.put("success", false);
            response.put("error", "Неавторизованный доступ");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        if (!CsrfTokenManager.validateToken(req)) {
            response.put("success", false);
            response.put("error", "Ошибка CSRF-проверки");
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> data = gson.fromJson(body, Map.class);

        String oldPassword = data.get("oldPassword");
        String newPassword = data.get("newPassword");
        String confirmPassword = data.get("confirmPassword");

        // Валидация
        if (oldPassword == null || oldPassword.isEmpty()) {
            response.put("success", false);
            response.put("error", "Введите текущий пароль");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        if (newPassword == null || newPassword.isEmpty()) {
            response.put("success", false);
            response.put("error", "Введите новый пароль");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        if (newPassword.length() < 6) {
            response.put("success", false);
            response.put("error", "Новый пароль должен содержать минимум 6 символов");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            response.put("success", false);
            response.put("error", "Пароли не совпадают");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String storedHash = getTeacherPasswordHash(conn);

            // Проверяем старый пароль через BCrypt
            if (storedHash == null || !BCrypt.checkpw(oldPassword, storedHash)) {
                response.put("success", false);
                response.put("error", "Неверный текущий пароль");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            // Хешируем новый пароль и сохраняем
            String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            updateTeacherPasswordHash(conn, newHash);

            response.put("success", true);
            response.put("message", "Пароль успешно изменён. Используйте новый пароль для входа.");
            log.info("Teacher password changed successfully");

        } catch (SQLException e) {
            log.error("Database error while changing password", e);
            response.put("success", false);
            response.put("error", "Ошибка базы данных: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    private String getTeacherPasswordHash(Connection conn) throws SQLException {
        String sql = "SELECT setting_value FROM teacher_settings WHERE setting_key = 'password'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("setting_value");
            }
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

    private void updateTeacherPasswordHash(Connection conn, String newHash) throws SQLException {
        String sql = "UPDATE teacher_settings SET setting_value = ?, updated_at = CURRENT_TIMESTAMP WHERE setting_key = 'password'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newHash);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                createDefaultPasswordHash(conn);
                try (PreparedStatement stmt2 = conn.prepareStatement(sql)) {
                    stmt2.setString(1, newHash);
                    stmt2.executeUpdate();
                }
            }
            log.info("Password hash updated in database");
        }
    }
}