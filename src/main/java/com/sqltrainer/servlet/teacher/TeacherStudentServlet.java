package com.sqltrainer.servlet.teacher;

import com.sqltrainer.config.DatabaseConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sqltrainer.util.Constants;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Сервлет для управления студентами преподавателем.
 * Позволяет генерировать, просматривать, редактировать и удалять студентов.
 */
@WebServlet("/api/teacher/students/*")
public class TeacherStudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(TeacherStudentServlet.class);
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    // Настройки генерации паролей
    private static final String UPPERCASE = Constants.PASSWORD_UPPERCASE;
    private static final String LOWERCASE = Constants.PASSWORD_LOWERCASE;
    private static final String DIGITS = Constants.PASSWORD_DIGITS;
    private static final String SPECIAL = Constants.PASSWORD_SPECIAL;
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo();
        log.info("GET request to: {}", path);

        if ("/list".equals(path)) {
            handleListStudents(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String action = req.getParameter("action");
        Map<String, Object> response = new HashMap<>();

        log.info("POST request with action: {}", action);

        if ("generate".equals(action)) {
            handleGenerateStudents(req, resp, response);
        } else {
            response.put("error", "Unknown action: " + action);
            resp.getWriter().write(gson.toJson(response));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo();
        log.info("PUT request to: {}", path);

        if ("/update".equals(path)) {
            handleUpdateStudent(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String path = req.getPathInfo();
        log.info("DELETE request to: {}", path);

        if ("/delete".equals(path)) {
            handleDeleteStudent(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    /**
     * Генерирует указанное количество студентов для группы.
     * Пароли генерируются случайным образом и отображаются только один раз.
     */
    private void handleGenerateStudents(HttpServletRequest req, HttpServletResponse resp, Map<String, Object> response) throws IOException {
        String groupName = req.getParameter("groupName");
        int count = Integer.parseInt(req.getParameter("count"));

        log.info("Generating {} students for group: {}", count, groupName);

        if (groupName == null || groupName.trim().isEmpty()) {
            response.put("error", "Group name is required");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        if (count < 1 || count > Constants.MAX_STUDENTS_PER_GENERATION) {
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

        resp.getWriter().write(gson.toJson(response));
    }

    /**
     * Генерирует безопасный пароль, содержащий заглавные и строчные буквы,
     * цифры и специальные символы.
     */
    private String generateSecurePassword() {
        Random random = new Random();
        StringBuilder password = new StringBuilder();

        password.append(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        password.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(random.nextInt(SPECIAL.length())));

        for (int i = 4; i < Constants.GENERATED_PASSWORD_LENGTH; i++) {
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

    /**
     * Вставляет студентов в базу данных.
     */
    private List<Map<String, String>> generateStudents(Connection conn, String groupName, int count) throws SQLException {
        List<Map<String, String>> students = new ArrayList<>();
        int nextId = getNextUserId(conn);

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
                stmt.setString(5, groupName);
                stmt.executeUpdate();
            }

            Map<String, String> student = new HashMap<>();
            student.put("login", login);
            student.put("password", password);
            student.put("fullName", fullName);
            student.put("email", email);
            student.put("groupName", groupName);
            students.add(student);
        }

        return students;
    }

    /**
     * Получает следующий доступный ID пользователя.
     */
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

    /**
     * Возвращает список студентов с возможностью фильтрации.
     */
    private void handleListStudents(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String search = req.getParameter("search");
        String group = req.getParameter("group");

        log.info("Listing students with search='{}', group='{}'", search, group);

        Map<String, Object> response = new HashMap<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            StringBuilder sql = new StringBuilder("SELECT id, login, full_name, email, role, group_name FROM users WHERE role = 'student'");
            List<Object> params = new ArrayList<>();

            if (search != null && !search.isEmpty()) {
                sql.append(" AND (full_name ILIKE ? OR login ILIKE ? OR email ILIKE ?)");
                String likePattern = "%" + search + "%";
                params.add(likePattern);
                params.add(likePattern);
                params.add(likePattern);
            }

            if (group != null && !group.isEmpty()) {
                sql.append(" AND group_name = ?");
                params.add(group);
            }

            sql.append(" ORDER BY id");

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                ResultSet rs = stmt.executeQuery();

                List<Map<String, Object>> students = new ArrayList<>();
                Set<String> groups = new HashSet<>();

                while (rs.next()) {
                    Map<String, Object> student = new HashMap<>();
                    student.put("id", rs.getInt("id"));
                    student.put("login", rs.getString("login"));
                    student.put("fullName", rs.getString("full_name"));
                    student.put("email", rs.getString("email"));
                    student.put("role", rs.getString("role"));
                    student.put("groupName", rs.getString("group_name"));
                    students.add(student);

                    String groupName = rs.getString("group_name");
                    if (groupName != null && !groupName.isEmpty()) {
                        groups.add(groupName);
                    }
                }

                response.put("success", true);
                response.put("students", students);
                response.put("groups", groups);
                log.info("Listed {} students", students.size());
            }
        } catch (SQLException e) {
            log.error("Failed to list students: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    /**
     * Обновляет информацию о студенте.
     */
    private void handleUpdateStudent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        Map<String, Object> data = gson.fromJson(body, Map.class);

        int id = ((Double) data.get("id")).intValue();
        String fullName = (String) data.get("fullName");
        String email = (String) data.get("email");
        String groupName = (String) data.get("groupName");

        log.info("Updating student id={}, fullName={}, email={}, groupName={}", id, fullName, email, groupName);

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            String sql = "UPDATE users SET full_name = ?, email = ?, group_name = ? WHERE id = ? AND role = 'student'";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fullName);
                stmt.setString(2, email);
                stmt.setString(3, groupName);
                stmt.setInt(4, id);
                stmt.executeUpdate();
            }
            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException e) {
            log.error("Failed to update student: {}", e.getMessage());
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Удаляет студента и все связанные с ним refresh токены.
     */
    private void handleDeleteStudent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int id = Integer.parseInt(req.getParameter("id"));

        log.info("Deleting student id={}", id);

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.ADMIN, null)) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM refresh_tokens WHERE user_id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE id = ? AND role = 'student'")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException e) {
            log.error("Failed to delete student: {}", e.getMessage());
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}