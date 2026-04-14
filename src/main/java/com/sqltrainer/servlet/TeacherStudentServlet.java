package com.sqltrainer.servlet;

import com.sqltrainer.config.DatabaseConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/teacher/students")
public class TeacherStudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(TeacherStudentServlet.class);
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;
    private static final int PASSWORD_LENGTH = 10;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        Map<String, Object> response = new HashMap<>();

        if ("generate".equals(action)) {
            String groupName = req.getParameter("groupName");
            int count = Integer.parseInt(req.getParameter("count"));

            log.info("Generating {} students for group: {}", count, groupName);

            if (groupName == null || groupName.trim().isEmpty()) {
                response.put("error", "Group name is required");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            if (count < 1 || count > 100) {
                response.put("error", "Count must be between 1 and 100");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
                List<Map<String, String>> students = generateStudents(conn, groupName.trim(), count);
                response.put("success", true);
                response.put("students", students);
                response.put("message", "Generated " + count + " students");
                log.info("Successfully generated {} students", students.size());
            } catch (SQLException e) {
                log.error("Failed to generate students: {}", e.getMessage());
                response.put("error", e.getMessage());
            }
        } else {
            response.put("error", "Unknown action: " + action);
        }

        String jsonResponse = gson.toJson(response);
        resp.getWriter().write(jsonResponse);
    }

    private String generateSecurePassword() {
        Random random = new Random();
        StringBuilder password = new StringBuilder();

        password.append(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        password.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(random.nextInt(SPECIAL.length())));

        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password.append(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
        }

        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    private List<Map<String, String>> generateStudents(Connection conn, String groupName, int count) throws SQLException {
        List<Map<String, String>> students = new ArrayList<>();
        int nextId = getNextUserId(conn);

        // Преобразуем группу в правильную UTF-8
        String correctGroupName = new String(groupName.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        log.info("Original groupName: {}, converted: {}", groupName, correctGroupName);

        for (int i = 0; i < count; i++) {
            int userNumber = nextId + i;
            String login = "student_" + userNumber;
            String password = generateSecurePassword();
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10));
            String fullName = "Student_" + userNumber;
            String email = login + "@student.sqltrainer.com";

            String sql = "INSERT INTO users (login, password_hash, email, full_name, role, group_name, is_active) " +
                    "VALUES (?, ?, ?, ?, 'student', ?, true) " +
                    "ON CONFLICT (login) DO UPDATE SET " +
                    "password_hash = EXCLUDED.password_hash, " +
                    "email = EXCLUDED.email, " +
                    "full_name = EXCLUDED.full_name, " +
                    "group_name = EXCLUDED.group_name";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, login);
                stmt.setString(2, passwordHash);
                stmt.setString(3, email);
                stmt.setString(4, fullName);
                stmt.setString(5, correctGroupName);
                stmt.executeUpdate();
            }

            Map<String, String> student = new HashMap<>();
            student.put("login", login);
            student.put("password", password);
            student.put("fullName", fullName);
            student.put("email", email);
            student.put("groupName", correctGroupName);
            students.add(student);
        }

        return students;
    }

    private int getNextUserId(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 as next_id FROM users";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("next_id");
            }
        }
        return 1;
    }
}