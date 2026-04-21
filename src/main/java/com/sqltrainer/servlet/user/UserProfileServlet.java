package com.sqltrainer.servlet.user;

import com.sqltrainer.config.DatabaseConfig;
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
import java.util.regex.Pattern;

@WebServlet("/api/user/*")
public class UserProfileServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(UserProfileServlet.class);
    private final Gson gson = new Gson();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo();

        if ("/profile".equals(path)) {
            handleGetProfile(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo();

        if ("/profile".equals(path)) {
            handleUpdateProfile(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    private void handleGetProfile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = (Long) req.getAttribute("userId");

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "SELECT login, full_name, email, role, group_name FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    Map<String, Object> user = new HashMap<>();
                    user.put("login", rs.getString("login"));

                    String fullName = rs.getString("full_name");
                    if (fullName == null) fullName = "";

                    String[] parts = parseFullName(fullName);
                    user.put("lastName", parts[0]);
                    user.put("firstName", parts[1]);
                    user.put("patronymic", parts[2]);
                    user.put("fullName", fullName);

                    user.put("email", rs.getString("email"));
                    user.put("role", rs.getString("role"));

                    String groupName = rs.getString("group_name");
                    user.put("groupName", groupName == null ? "" : groupName);

                    response.put("user", user);
                    resp.getWriter().write(gson.toJson(response));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"User not found\"}");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get profile: {}", e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private String[] parseFullName(String fullName) {
        String[] result = {"", "", ""};
        if (fullName != null && !fullName.isEmpty()) {
            String[] parts = fullName.split(" ");
            if (parts.length >= 1) result[0] = parts[0];
            if (parts.length >= 2) result[1] = parts[1];
            if (parts.length >= 3) result[2] = parts[2];
        }
        return result;
    }

    private String buildFullName(String lastName, String firstName, String patronymic) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null && !lastName.isEmpty()) sb.append(lastName);
        if (firstName != null && !firstName.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(firstName);
        }
        if (patronymic != null && !patronymic.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(patronymic);
        }
        return sb.toString();
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if ("!@#$%^&*".indexOf(c) != -1) hasSpecial = true;
        }

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private void handleUpdateProfile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = (Long) req.getAttribute("userId");
        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, String> data = gson.fromJson(body, Map.class);

        String lastName = data.get("lastName");
        String firstName = data.get("firstName");
        String patronymic = data.get("patronymic");
        String email = data.get("email");
        String currentPassword = data.get("currentPassword");
        String newPassword = data.get("newPassword");

        String fullName = buildFullName(lastName, firstName, patronymic);

        if (email != null && !email.isEmpty() && !isValidEmail(email)) {
            resp.getWriter().write("{\"error\":\"Invalid email format\"}");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String currentHash = null;
            String checkSql = "SELECT password_hash FROM users WHERE id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, userId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    currentHash = rs.getString("password_hash");
                }
            }

            if (newPassword != null && !newPassword.isEmpty()) {
                if (currentPassword == null || currentPassword.isEmpty()) {
                    resp.getWriter().write("{\"error\":\"Current password is required to change password\"}");
                    return;
                }

                if (!BCrypt.checkpw(currentPassword, currentHash)) {
                    resp.getWriter().write("{\"error\":\"Current password is incorrect\"}");
                    return;
                }

                if (BCrypt.checkpw(newPassword, currentHash)) {
                    resp.getWriter().write("{\"error\":\"New password cannot be the same as the current password\"}");
                    return;
                }

                if (!isStrongPassword(newPassword)) {
                    resp.getWriter().write("{\"error\":\"Password does not meet security requirements\"}");
                    return;
                }

                String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(10));
                String sql = "UPDATE users SET full_name = ?, email = ?, password_hash = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, fullName);
                    stmt.setString(2, email);
                    stmt.setString(3, newHash);
                    stmt.setLong(4, userId);
                    stmt.executeUpdate();
                }
            } else {
                String sql = "UPDATE users SET full_name = ?, email = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, fullName);
                    stmt.setString(2, email);
                    stmt.setLong(3, userId);
                    stmt.executeUpdate();
                }
            }

            resp.getWriter().write("{\"success\":true,\"message\":\"Profile updated successfully\"}");
        } catch (SQLException e) {
            log.error("Failed to update profile: {}", e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}