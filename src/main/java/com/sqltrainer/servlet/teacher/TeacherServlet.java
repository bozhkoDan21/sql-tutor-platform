package com.sqltrainer.servlet.teacher;

import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.servlet.log.LogStreamServlet;
import com.sqltrainer.servlet.student.StudentServlet;
import com.sqltrainer.util.Constants;
import com.sqltrainer.util.QueryExecutor;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Сервлет для управления учебными базами данных преподавателем.
 * Позволяет создавать новые БД из SQL-скриптов, удалять существующие,
 * просматривать активные сессии студентов и завершать их.
 */
@WebServlet("/api/teacher")
@MultipartConfig(
        maxFileSize = Constants.MAX_FILE_SIZE_BYTES,
        maxRequestSize = Constants.MAX_REQUEST_SIZE_BYTES
)
public class TeacherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(TeacherServlet.class);
    private final Gson gson = new Gson();

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(Constants.ALLOWED_SQL_EXTENSIONS));
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // Защищённые базы данных, которые нельзя удалить
    private static final Set<String> PROTECTED_DATABASES = new HashSet<>(Arrays.asList(
            Constants.PROTECTED_DATABASES
    ));

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        Map<String, Object> response = new HashMap<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, "postgres")) {

            if ("upload".equals(action)) {
                String dbName = req.getParameter("dbName");
                Part filePart = req.getPart("sqlFile");
                handleUpload(conn, dbName, filePart, response);

            } else if ("list".equals(action)) {
                response.put("databases", getDatabaseList(conn));

            } else if ("delete".equals(action)) {
                String dbName = req.getParameter("dbName");
                handleDelete(conn, dbName, response);

            } else if ("sessions".equals(action)) {
                Map<String, StudentServlet.SessionInfo> sessions = StudentServlet.getActiveSessions();
                response.put("sessions", new ArrayList<>(sessions.values()));
            } else if ("terminateSession".equals(action)) {
                String sessionId = req.getParameter("sessionId");
                handleTerminateSession(sessionId, response);
            } else {
                response.put("error", "Unknown action: " + action);
            }

        } catch (Exception e) {
            log.error("Teacher action failed: {}", action, e);
            response.put("error", e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doPost(req, resp);
    }

    /**
     * Завершает сессию студента.
     */
    private void handleTerminateSession(String sessionId, Map<String, Object> response) {
        if (sessionId == null || sessionId.isEmpty()) {
            response.put("error", "Session ID is required");
            return;
        }

        Map<String, StudentServlet.SessionInfo> sessions = StudentServlet.getActiveSessions();
        StudentServlet.SessionInfo info = sessions.get(sessionId);

        if (info != null) {
            info.setBlocked(true);
            response.put("success", true);
            response.put("message", "Session terminated: " + sessionId.substring(0, 8) + "...");
            log.info("Teacher terminated session: {} for user {}", sessionId.substring(0, 8), info.getLogin());
        } else {
            response.put("error", "Session not found or already terminated");
            log.warn("Session not found: {}", sessionId);
        }
    }

    /**
     * Экранирует идентификатор для безопасного использования в SQL.
     * Использует встроенную функцию PostgreSQL quote_ident.
     */
    private String quoteIdent(Connection conn, String identifier) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT quote_ident(?)")) {
            stmt.setString(1, identifier);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return identifier;
    }

    /**
     * Обрабатывает загрузку SQL-скрипта и создание новой базы данных.
     */
    private void handleUpload(Connection conn, String dbName, Part filePart,
                              Map<String, Object> response) throws IOException, SQLException {

        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }

        if (PROTECTED_DATABASES.contains(dbName.toLowerCase())) {
            response.put("error", "Cannot create database with reserved name: " + dbName);
            return;
        }

        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            response.put("error", "Database name can only contain letters, numbers and underscores");
            return;
        }
        if (dbName.length() > Constants.MAX_DB_NAME_LENGTH) {
            response.put("error", "Database name too long (max 63 characters)");
            return;
        }

        if (filePart == null || filePart.getSize() == 0) {
            response.put("error", "SQL file is required");
            return;
        }

        String fileName = getFileName(filePart);
        if (!isValidFileExtension(fileName)) {
            response.put("error", "Only .sql files are allowed");
            return;
        }

        String script = readSqlScript(filePart);
        if (script.length() > Constants.MAX_SCRIPT_SIZE_BYTES) {
            response.put("error", "SQL script too large (max 2MB)");
            return;
        }
        if (!isValidSqlContent(script)) {
            response.put("error", "Invalid SQL content. Script must contain CREATE TABLE or INSERT statements");
            return;
        }

        if (databaseExists(conn, dbName)) {
            response.put("error", "Database with name '" + dbName + "' already exists");
            return;
        }

        QueryExecutor.clearCache();
        log.info("Cache cleared before creating database: {}", dbName);

        String quotedDbName = quoteIdent(conn, dbName);

        try (Statement stmt = conn.createStatement()) {
            String createSql = "CREATE DATABASE " + quotedDbName + " OWNER teacher_role";
            log.debug("Executing: {}", createSql);
            stmt.executeUpdate(createSql);
        } catch (SQLException e) {
            log.error("Failed to create database: {}", dbName, e);
            response.put("error", "Failed to create database: " + e.getMessage());
            return;
        }

        try (Connection dbConn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
             Statement stmt = dbConn.createStatement()) {

            dbConn.setAutoCommit(false);
            try {
                executeSqlScriptWithLogs(stmt, script);
                dbConn.commit();
            } catch (SQLException e) {
                dbConn.rollback();
                dropDatabase(conn, quotedDbName, dbName);
                log.error("Failed to execute SQL script on {}", dbName, e);
                response.put("error", "Failed to execute SQL script: " + e.getMessage());
                return;
            }
        }

        try {
            grantStudentAccess(conn, quotedDbName, dbName);
        } catch (SQLException e) {
            log.warn("Could not grant student access to {}: {}", dbName, e.getMessage());
        }

        response.put("success", true);
        response.put("message", "Database '" + dbName + "' created successfully");
    }

    private boolean isValidFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) return false;
        String ext = fileName.substring(lastDot + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    /**
     * Проверяет содержимое SQL-скрипта на наличие опасных команд.
     */
    private boolean isValidSqlContent(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String lower = content.toLowerCase();

        for (String cmd : Constants.DANGEROUS_SCRIPT_PATTERNS) {
            if (lower.contains(cmd)) return false;
        }

        return lower.contains("create table") ||
                lower.contains("insert into") ||
                lower.contains("create database");
    }

    private boolean databaseExists(Connection conn, String dbName) throws SQLException {
        String sql = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dbName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void dropDatabase(Connection conn, String quotedDbName, String originalDbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String dropSql = "DROP DATABASE IF EXISTS " + quotedDbName;
            log.debug("Executing: {}", dropSql);
            stmt.executeUpdate(dropSql);
        } catch (SQLException e) {
            log.error("Failed to drop database: {}", originalDbName, e);
            throw e;
        }
    }

    /**
     * Предоставляет доступ студентам к созданной базе данных.
     */
    private void grantStudentAccess(Connection conn, String quotedDbName, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String grantConnectSql = "GRANT CONNECT ON DATABASE " + quotedDbName + " TO students";
            log.debug("Executing: {}", grantConnectSql);
            stmt.executeUpdate(grantConnectSql);
        }

        try (Connection dbConn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
             Statement stmt = dbConn.createStatement()) {
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO students");
        }
    }

    private String readSqlScript(Part filePart) throws IOException {
        StringBuilder script = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(filePart.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                script.append(line).append("\n");
            }
        }
        return script.toString();
    }

    /**
     * Выполняет SQL-скрипт и отправляет прогресс через SSE логи.
     */
    private void executeSqlScriptWithLogs(Statement stmt, String script) throws SQLException {
        String[] lines = script.split("\n");
        StringBuilder currentQuery = new StringBuilder();
        boolean inBlockComment = false;
        int queryCount = 0;
        int totalQueries = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--") &&
                    !trimmed.startsWith("/*") && trimmed.endsWith(";")) {
                totalQueries++;
            }
        }

        if (totalQueries == 0) totalQueries = 100;

        log.info("Starting SQL script execution. Total queries: {}", totalQueries);
        LogStreamServlet.addLog("{\"type\":\"start\",\"total\":" + totalQueries + "}");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("/*")) inBlockComment = true;
            if (inBlockComment) {
                if (trimmed.contains("*/")) inBlockComment = false;
                continue;
            }
            if (trimmed.startsWith("--")) continue;

            currentQuery.append(line).append(" ");
            if (trimmed.endsWith(";")) {
                String query = currentQuery.toString().trim();
                if (!query.isEmpty() && !";".equals(query)) {
                    queryCount++;

                    String shortQuery = query.length() > 50 ?
                            query.substring(0, 50) + "..." : query;

                    String logMessage = String.format(
                            "{\"type\":\"progress\",\"current\":%d,\"total\":%d,\"query\":\"%s\"}",
                            queryCount, totalQueries, shortQuery.replace("\"", "\\\"")
                    );

                    log.debug("Executing query {}/{}: {}", queryCount, totalQueries, shortQuery);
                    LogStreamServlet.addLog(logMessage);

                    try {
                        stmt.execute(query);
                        LogStreamServlet.addLog(String.format(
                                "{\"type\":\"success\",\"current\":%d,\"message\":\"Query %d completed\"}",
                                queryCount, queryCount));
                    } catch (SQLException e) {
                        LogStreamServlet.addLog(String.format(
                                "{\"type\":\"error\",\"query\":\"%s\",\"error\":\"%s\"}",
                                shortQuery, e.getMessage().replace("\"", "\\\"").replace("\n", " ")));
                        throw e;
                    }
                }
                currentQuery = new StringBuilder();
            }
        }

        log.info("SQL script execution completed. Total queries: {}", queryCount);
        LogStreamServlet.addLog("{\"type\":\"complete\",\"message\":\"Script execution completed\"}");
    }

    private List<String> getDatabaseList(Connection conn) throws SQLException {
        List<String> databases = new ArrayList<>();
        String sql = "SELECT datname FROM pg_database " +
                "WHERE datistemplate = false " +
                "AND datname NOT IN ('postgres', 'template0', 'template1') " +
                "ORDER BY datname";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                databases.add(rs.getString("datname"));
            }
        }
        return databases;
    }

    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition != null) {
            for (String cd : contentDisposition.split(";")) {
                if (cd.trim().startsWith("filename")) {
                    String fileName = cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
                    return fileName.substring(fileName.lastIndexOf('\\') + 1);
                }
            }
        }
        return null;
    }

    /**
     * Удаляет базу данных. Защищённые базы удалить нельзя.
     */
    private void handleDelete(Connection conn, String dbName, Map<String, Object> response) {
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }

        if (PROTECTED_DATABASES.contains(dbName.toLowerCase())) {
            response.put("error", "Cannot delete protected database: " + dbName +
                    ". This is a system or demo database required for learning.");
            return;
        }

        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            response.put("error", "Invalid database name");
            return;
        }

        try {
            QueryExecutor.clearCache();
            log.info("Cache cleared before deleting database: {}", dbName);

            String quotedDbName = quoteIdent(conn, dbName);

            try (Statement stmt = conn.createStatement()) {
                try {
                    String terminateSql = String.format(
                            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
                                    "WHERE datname = %s AND pid <> pg_backend_pid()",
                            quotedDbName
                    );
                    log.debug("Executing: {}", terminateSql);
                    stmt.execute(terminateSql);
                } catch (SQLException e) {
                    log.debug("No active connections to terminate for {}", dbName);
                }

                String dropSql = "DROP DATABASE IF EXISTS " + quotedDbName;
                log.debug("Executing: {}", dropSql);
                stmt.executeUpdate(dropSql);

                DatabaseConfig.closeStudentPool(dbName);
                DatabaseConfig.closeTeacherPool(dbName);

                response.put("success", true);
                response.put("message", "Database '" + dbName + "' deleted");
            }
        } catch (SQLException e) {
            log.error("Failed to delete database: {}", dbName, e);
            response.put("error", "Failed to delete database: " + e.getMessage());
        }
    }
}