package com.sqltrainer.servlet;

import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.util.JwtUtil;
import com.google.gson.Gson;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AuthServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        switch (path) {
            case "/login":
                handleLogin(req, resp);
                break;
            case "/logout":
                handleLogout(req, resp);
                break;
            case "/refresh":
                handleRefresh(req, resp);
                break;
            default:
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if ("/me".equals(path)) {
            handleGetMe(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    /**
     * Единый вход для всех пользователей (и студенты, и преподаватели)
     * Роль определяется автоматически из БД
     */
    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> loginData = gson.fromJson(body, Map.class);

        String login = loginData.get("login");
        String password = loginData.get("password");

        if (login == null || password == null) {
            sendError(resp, 400, "Login and password are required");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT id, login, full_name, email, role, password_hash, is_active FROM users WHERE login = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, login);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    if (!rs.getBoolean("is_active")) {
                        sendError(resp, 401, "Account is deactivated. Please contact administrator.");
                        return;
                    }

                    if (BCrypt.checkpw(password, rs.getString("password_hash"))) {
                        Long userId = rs.getLong("id");
                        String role = rs.getString("role");
                        String accessToken = JwtUtil.generateAccessToken(userId, role, login);
                        String refreshToken = JwtUtil.generateRefreshToken(userId, role);

                        saveRefreshToken(conn, userId, refreshToken);
                        updateLastActivity(conn, userId);

                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("accessToken", accessToken);
                        response.put("refreshToken", refreshToken);
                        response.put("tokenType", "Bearer");

                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", userId);
                        userMap.put("login", login);
                        userMap.put("fullName", rs.getString("full_name"));
                        userMap.put("email", rs.getString("email"));
                        userMap.put("role", role);
                        response.put("user", userMap);

                        resp.getWriter().write(gson.toJson(response));
                    } else {
                        sendError(resp, 401, "Invalid login or password");
                    }
                } else {
                    sendError(resp, 401, "Invalid login or password");
                }
            }
        } catch (SQLException e) {
            log.error("Login failed: {}", e.getMessage());
            sendError(resp, 500, "Database error: " + e.getMessage());
        }
    }

    /**
     * Обновление access token по refresh token
     */
    private void handleRefresh(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> data = gson.fromJson(body, Map.class);
        String refreshToken = data.get("refreshToken");

        if (refreshToken == null || !JwtUtil.isValidRefreshToken(refreshToken)) {
            sendError(resp, 401, "Invalid refresh token");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT user_id FROM refresh_tokens WHERE token = ? AND revoked = false AND expires_at > NOW()";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, refreshToken);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    Long userId = rs.getLong("user_id");

                    // Получаем данные пользователя
                    String userSql = "SELECT login, role FROM users WHERE id = ?";
                    try (PreparedStatement userStmt = conn.prepareStatement(userSql)) {
                        userStmt.setLong(1, userId);
                        ResultSet userRs = userStmt.executeQuery();
                        if (userRs.next()) {
                            String login = userRs.getString("login");
                            String role = userRs.getString("role");
                            String newAccessToken = JwtUtil.generateAccessToken(userId, role, login);

                            Map<String, Object> response = new HashMap<>();
                            response.put("success", true);
                            response.put("accessToken", newAccessToken);
                            resp.getWriter().write(gson.toJson(response));
                        } else {
                            sendError(resp, 401, "User not found");
                        }
                    }
                } else {
                    sendError(resp, 401, "Invalid refresh token");
                }
            }
        } catch (SQLException e) {
            log.error("Refresh failed: {}", e.getMessage());
            sendError(resp, 500, "Database error");
        }
    }

    /**
     * Выход из системы (отзыв refresh token)
     */
    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Long userId = JwtUtil.getUserIdFromToken(token);
                try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
                    String sql = "UPDATE refresh_tokens SET revoked = true WHERE user_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setLong(1, userId);
                        stmt.executeUpdate();
                    }
                }
            } catch (Exception e) {
                log.warn("Logout error: {}", e.getMessage());
            }
        }
        resp.getWriter().write("{\"success\":true}");
    }

    /**
     * Получение информации о текущем пользователе
     */
    private void handleGetMe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = (Long) req.getAttribute("userId");
        String role = (String) req.getAttribute("role");

        if (userId == null) {
            sendError(resp, 401, "Not authenticated");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT id, login, full_name, email, role, group_name, avatar_url FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);

                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("id", rs.getLong("id"));
                    userMap.put("login", rs.getString("login"));
                    userMap.put("fullName", rs.getString("full_name"));
                    userMap.put("email", rs.getString("email"));
                    userMap.put("role", rs.getString("role"));
                    userMap.put("groupName", rs.getString("group_name"));
                    userMap.put("avatarUrl", rs.getString("avatar_url"));
                    response.put("user", userMap);

                    resp.getWriter().write(gson.toJson(response));
                } else {
                    sendError(resp, 404, "User not found");
                }
            }
        } catch (SQLException e) {
            log.error("Get me error: {}", e.getMessage());
            sendError(resp, 500, "Database error");
        }
    }

    /**
     * Сохраняет refresh token в БД
     */
    private void saveRefreshToken(Connection conn, Long userId, String token) throws SQLException {
        String sql = "INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES (?, ?, NOW() + INTERVAL '7 days')";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, token);
            stmt.executeUpdate();
        }
    }

    /**
     * Обновляет время последней активности пользователя
     */
    private void updateLastActivity(Connection conn, Long userId) throws SQLException {
        String sql = "UPDATE users SET last_activity_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}