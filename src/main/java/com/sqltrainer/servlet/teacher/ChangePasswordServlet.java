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
import java.security.SecureRandom;

@WebServlet("/api/teacher/change-password")
public class ChangePasswordServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordServlet.class);
    private final Gson gson = new Gson();
    private static final SecureRandom random = new SecureRandom();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> response = new HashMap<>();

        // 1. Проверяем авторизацию
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("authenticated") == null) {
            response.put("success", false);
            response.put("error", "Неавторизованный доступ");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // 2. Проверяем CSRF
        if (!CsrfTokenManager.validateToken(req)) {
            response.put("success", false);
            response.put("error", "Ошибка CSRF-проверки");
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // 3. Получаем логин из сессии
        String username = (String) session.getAttribute("username");
        if (username == null || username.isEmpty()) {
            response.put("success", false);
            response.put("error", "Не удалось определить пользователя");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // 4. Читаем тело запроса
        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> data = gson.fromJson(body, Map.class);

        String oldPassword = data.get("oldPassword");
        String newPassword = data.get("newPassword");
        String confirmPassword = data.get("confirmPassword");

        // 5. Валидация
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

        // 6. Проверяем старый пароль
        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            // Проверяем старый пароль
            String checkSql = "SELECT password_hash FROM teacher_settings WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();

                if (!rs.next()) {
                    response.put("success", false);
                    response.put("error", "Пользователь не найден");
                    resp.getWriter().write(gson.toJson(response));
                    return;
                }

                String storedHash = rs.getString("password_hash");

                if (!BCrypt.checkpw(oldPassword, storedHash)) {
                    response.put("success", false);
                    response.put("error", "Неверный текущий пароль");
                    resp.getWriter().write(gson.toJson(response));
                    return;
                }
            }

            // Проверяем уникальность нового пароля (чтобы не было одинаковых)
            String checkUniqueSql = "SELECT username FROM teacher_settings WHERE password_hash = ? AND username != ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkUniqueSql)) {
                String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
                stmt.setString(1, newHash);
                stmt.setString(2, username);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    response.put("success", false);
                    response.put("error", "Этот пароль уже используется другим преподавателем!");
                    resp.getWriter().write(gson.toJson(response));
                    return;
                }
            }

            // Обновляем пароль
            String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            String updateSql = "UPDATE teacher_settings SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, newHash);
                updateStmt.setString(2, username);
                updateStmt.executeUpdate();
            }

            response.put("success", true);
            response.put("message", "Пароль успешно изменён");
            log.info("Teacher '{}' changed password successfully", username);

        } catch (SQLException e) {
            log.error("Database error while changing password for user: {}", username, e);
            response.put("success", false);
            response.put("error", "Ошибка базы данных: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    // ================================================================
    // НОВЫЙ ЭНДПОИНТ: ГЕНЕРАЦИЯ СЛУЧАЙНОГО ПАРОЛЯ
    // ================================================================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> response = new HashMap<>();

        // Проверяем авторизацию
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("authenticated") == null) {
            response.put("success", false);
            response.put("error", "Неавторизованный доступ");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // Генерируем случайный пароль (8 символов)
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        response.put("success", true);
        response.put("password", password.toString());
        resp.getWriter().write(gson.toJson(response));
    }
}