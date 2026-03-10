package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

@WebServlet("/api/teacher")
@MultipartConfig(
        maxFileSize = 1024 * 1024 * 10,      // 10 MB
        maxRequestSize = 1024 * 1024 * 15    // 15 MB
)
public class TeacherServlet extends HttpServlet {

    private Gson gson = new Gson();

    // Список разрешенных расширений файлов
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("sql"));

    // Максимальный размер скрипта в символах
    private static final int MAX_SCRIPT_LENGTH = 10_000_000;

    // Паттерн для проверки имени базы данных (только буквы, цифры и подчеркивание)
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        Map<String, Object> response = new HashMap<>();

        try {
            // Преподаватель подключается под своей ролью
            try (Connection conn = DatabaseConfig.getConnection(
                    DatabaseConfig.Role.TEACHER, "postgres", null)) {

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
                    // Получаем активные сессии из StudentServlet
                    List<StudentServlet.SessionInfo> sessions =
                            new ArrayList<>(StudentServlet.activeSessions.values());
                    response.put("sessions", sessions);
                } else {
                    response.put("error", "Unknown action: " + action);
                }
            }

        } catch (Exception e) {
            response.put("error", e.getMessage());
            e.printStackTrace();
        }

        resp.getWriter().write(gson.toJson(response));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doPost(req, resp);
    }

    /**
     * Обработка загрузки нового SQL скрипта для создания базы данных
     */
    private void handleUpload(Connection conn, String dbName, Part filePart,
                              Map<String, Object> response)
            throws IOException, SQLException {

        // Проверка имени базы данных
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }
        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            response.put("error", "Database name can only contain letters, numbers and underscores");
            return;
        }
        if (dbName.length() > 63) {
            response.put("error", "Database name too long (max 63 characters)");
            return;
        }

        // Проверка файла
        if (filePart == null || filePart.getSize() == 0) {
            response.put("error", "SQL file is required");
            return;
        }

        // Проверка расширения файла
        String fileName = getFileName(filePart);
        if (!isValidFileExtension(fileName)) {
            response.put("error", "Only .sql files are allowed");
            return;
        }

        // Чтение и проверка SQL скрипта
        String script = readSqlScript(filePart);
        if (script.length() > MAX_SCRIPT_LENGTH) {
            response.put("error", "SQL script too large (max 10MB)");
            return;
        }
        if (!isValidSqlContent(script)) {
            response.put("error", "Invalid SQL content. Script must contain CREATE TABLE or INSERT statements");
            return;
        }

        // Проверка существования базы
        if (databaseExists(conn, dbName)) {
            response.put("error", "Database with name '" + dbName + "' already exists");
            return;
        }

        // Создание базы данных
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE " + dbName + " OWNER teacher_role");
        } catch (SQLException e) {
            response.put("error", "Failed to create database: " + e.getMessage());
            return;
        }

        // Выполнение SQL скрипта в новой базе
        try (Connection dbConn = DatabaseConfig.getConnection(
                DatabaseConfig.Role.TEACHER, dbName, null);
             Statement stmt = dbConn.createStatement()) {

            dbConn.setAutoCommit(false);
            try {
                // Используем метод с логированием (но в обычном режиме без PrintWriter)
                executeSqlScriptWithLogs(stmt, script);
                dbConn.commit();
            } catch (SQLException e) {
                dbConn.rollback();
                dropDatabase(conn, dbName);
                response.put("error", "Failed to execute SQL script: " + e.getMessage());
                return;
            }
        }

        // Устанавливаем права для студентов
        try {
            grantStudentAccess(conn, dbName);
        } catch (SQLException e) {
            System.err.println("Warning: Could not grant student access to " + dbName + ": " + e.getMessage());
        }

        response.put("success", true);
        response.put("message", "Database '" + dbName + "' created successfully");
    }

    private boolean isValidFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) return false;
        String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    private boolean isValidSqlContent(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String lowerContent = content.toLowerCase();

        // Запрещаем опасные команды
        if (lowerContent.contains("drop database") ||
                lowerContent.contains("drop table") ||
                lowerContent.contains("delete from") ||
                lowerContent.contains("truncate") ||
                lowerContent.contains("alter system") ||
                lowerContent.contains("pg_sleep") ||
                lowerContent.contains("copy")) {
            return false;
        }

        return lowerContent.contains("create table") ||
                lowerContent.contains("insert into") ||
                lowerContent.contains("create database");
    }

    private boolean databaseExists(Connection conn, String dbName) throws SQLException {
        String sql = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void dropDatabase(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
        }
    }

    private void grantStudentAccess(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("GRANT CONNECT ON DATABASE " + dbName + " TO students");
        }
        try (Connection dbConn = DatabaseConfig.getConnection(
                DatabaseConfig.Role.TEACHER, dbName, null);
             Statement stmt = dbConn.createStatement()) {
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO students");
        }
    }

    private String readSqlScript(Part filePart) throws IOException {
        StringBuilder script = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(filePart.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                script.append(line).append("\n");
            }
        }
        return script.toString();
    }

    /**
     * Выполнение SQL скрипта с отправкой логов клиенту
     */
    private void executeSqlScriptWithLogs(Statement stmt, String script) throws SQLException {
        String[] lines = script.split("\n");
        StringBuilder currentQuery = new StringBuilder();
        boolean inBlockComment = false;
        int queryCount = 0;
        int totalQueries = 0;

        // Подсчет общего количества запросов
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("--") &&
                    !trimmedLine.startsWith("/*") && trimmedLine.endsWith(";")) {
                totalQueries++;
            }
        }

        if (totalQueries == 0) totalQueries = 100;

        System.out.println("=== Starting SQL script execution. Total queries: " + totalQueries + " ===");
        com.sqltutor.servlet.LogStreamServlet.addLog("{\"type\":\"start\",\"total\":" + totalQueries + "}");

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;

            if (trimmedLine.startsWith("/*")) inBlockComment = true;
            if (inBlockComment) {
                if (trimmedLine.contains("*/")) inBlockComment = false;
                continue;
            }
            if (trimmedLine.startsWith("--")) continue;

            currentQuery.append(line).append(" ");
            if (trimmedLine.endsWith(";")) {
                String query = currentQuery.toString().trim();
                if (!query.isEmpty() && !query.equals(";")) {
                    queryCount++;

                    String shortQuery = query.length() > 50 ?
                            query.substring(0, 50) + "..." : query;

                    String logMessage = String.format(
                            "{\"type\":\"progress\",\"current\":%d,\"total\":%d,\"query\":\"%s\"}",
                            queryCount, totalQueries, shortQuery.replace("\"", "\\\"")
                    );

                    System.out.println("Executing query " + queryCount + ": " + shortQuery);
                    com.sqltutor.servlet.LogStreamServlet.addLog(logMessage);

                    try {
                        stmt.execute(query);
                        System.out.println("Query " + queryCount + " completed successfully");

                        String successLog = String.format(
                                "{\"type\":\"success\",\"current\":%d,\"message\":\"Query %d completed\"}",
                                queryCount, queryCount
                        );
                        com.sqltutor.servlet.LogStreamServlet.addLog(successLog);

                    } catch (SQLException e) {
                        String errorLog = String.format(
                                "{\"type\":\"error\",\"query\":\"%s\",\"error\":\"%s\"}",
                                shortQuery, e.getMessage().replace("\"", "\\\"").replace("\n", " ")
                        );
                        com.sqltutor.servlet.LogStreamServlet.addLog(errorLog);
                        throw e;
                    }
                }
                currentQuery = new StringBuilder();
            }
        }

        String lastQuery = currentQuery.toString().trim();
        if (!lastQuery.isEmpty() && !lastQuery.equals(";")) {
            queryCount++;
            com.sqltutor.servlet.LogStreamServlet.addLog("{\"type\":\"complete\",\"message\":\"Script execution completed\"}");
        }

        System.out.println("=== SQL script execution completed. Total queries: " + queryCount + " ===");
    }

    /**
     * Получение списка баз данных (только пользовательские, без системных)
     */
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

    /**
     * Получение имени файла из Part для Tomcat 7
     */
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition != null) {
            for (String cd : contentDisposition.split(";")) {
                if (cd.trim().startsWith("filename")) {
                    String fileName = cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
                    // Извлекаем только имя файла из полного пути
                    return fileName.substring(fileName.lastIndexOf('\\') + 1);
                }
            }
        }
        return null;
    }

    /**
     * Обработка удаления базы данных
     */
    private void handleDelete(Connection conn, String dbName,
                              Map<String, Object> response) throws SQLException {

        if (dbName == null || dbName.isEmpty() ||
                dbName.equals("postgres") || dbName.equals("template0") ||
                dbName.equals("template1")) {
            response.put("error", "Cannot delete system database");
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            // Завершаем все соединения к этой базе
            try {
                stmt.execute(
                        "SELECT pg_terminate_backend(pid) " +
                                "FROM pg_stat_activity " +
                                "WHERE datname = '" + dbName + "' AND pid <> pg_backend_pid()"
                );
            } catch (SQLException e) {
                // Игнорируем, если нет соединений
            }

            // Удаляем базу
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
            response.put("success", true);
            response.put("message", "Database '" + dbName + "' deleted");

        } catch (SQLException e) {
            response.put("error", "Failed to delete database: " + e.getMessage());
        }
    }
}