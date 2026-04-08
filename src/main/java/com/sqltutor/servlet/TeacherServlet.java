package com.sqltutor.servlet;

import com.sqltutor.config.DatabaseConfig;
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

@WebServlet("/api/teacher")
@MultipartConfig(
        maxFileSize = 1024 * 1024 * 10,      // 10 MB
        maxRequestSize = 1024 * 1024 * 15    // 15 MB
)
public class TeacherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(TeacherServlet.class);
    private final Gson gson = new Gson();

    private static final Set<String> ALLOWED_EXTENSIONS = Collections.singleton("sql");
    private static final int MAX_SCRIPT_LENGTH = 10_000_000;
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

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
                // Получаем активные сессии через публичный метод
                Map<String, StudentServlet.SessionInfo> sessions = StudentServlet.getActiveSessions();
                response.put("sessions", new ArrayList<>(sessions.values()));
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

    private void handleUpload(Connection conn, String dbName, Part filePart,
                              Map<String, Object> response) throws IOException, SQLException {

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
        if (script.length() > MAX_SCRIPT_LENGTH) {
            response.put("error", "SQL script too large (max 10MB)");
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

        // Создание базы данных
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE " + dbName + " OWNER teacher_role");
        } catch (SQLException e) {
            log.error("Failed to create database: {}", dbName, e);
            response.put("error", "Failed to create database: " + e.getMessage());
            return;
        }

        // Выполнение SQL скрипта
        try (Connection dbConn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
             Statement stmt = dbConn.createStatement()) {

            dbConn.setAutoCommit(false);
            try {
                executeSqlScriptWithLogs(stmt, script);
                dbConn.commit();
            } catch (SQLException e) {
                dbConn.rollback();
                dropDatabase(conn, dbName);
                log.error("Failed to execute SQL script on {}", dbName, e);
                response.put("error", "Failed to execute SQL script: " + e.getMessage());
                return;
            }
        }

        try {
            grantStudentAccess(conn, dbName);
        } catch (SQLException e) {
            log.warn("Could not grant student access to {}: {}", dbName, e.getMessage());
        }

        response.put("success", true);
        response.put("message", "Database '" + dbName + "' created successfully");
    }

    /**
     * Проверяет, имеет ли файл разрешённое расширение (.sql).
     *
     * @param fileName имя файла
     * @return true, если расширение .sql, иначе false
     */
    private boolean isValidFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) return false;
        String ext = fileName.substring(lastDot + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    /**
     * Проверяет содержимое SQL-скрипта на наличие опасных команд.
     * <p>
     * Запрещённые команды: DROP DATABASE, DROP TABLE, DELETE FROM, TRUNCATE,
     * ALTER SYSTEM, PG_SLEEP, COPY.
     * </p>
     * <p>
     * Разрешённые команды: CREATE TABLE, INSERT INTO, CREATE DATABASE.
     * </p>
     *
     * @param content содержимое SQL-скрипта
     * @return true, если содержимое безопасно и содержит разрешённые команды
     */
    private boolean isValidSqlContent(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String lower = content.toLowerCase();

        // Запрещаем опасные команды
        List<String> dangerous = Arrays.asList(
                "drop database", "drop table", "delete from", "truncate",
                "alter system", "pg_sleep", "copy"
        );
        for (String cmd : dangerous) {
            if (lower.contains(cmd)) return false;
        }

        return lower.contains("create table") ||
                lower.contains("insert into") ||
                lower.contains("create database");
    }

    /**
     * Проверяет существование базы данных с указанным именем.
     *
     * @param conn   соединение с БД (под ролью администратора)
     * @param dbName имя базы данных для проверки
     * @return true, если база данных существует
     * @throws SQLException при ошибках выполнения запроса
     */
    private boolean databaseExists(Connection conn, String dbName) throws SQLException {
        String sql = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dbName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Удаляет базу данных (вызывается при откате транзакции).
     *
     * @param conn   соединение с БД (под ролью администратора)
     * @param dbName имя удаляемой базы данных
     * @throws SQLException при ошибках выполнения
     */
    private void dropDatabase(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
        }
    }

    /**
     * Предоставляет студентам права на чтение созданной базы данных.
     * <p>
     * Выполняет:
     * <ul>
     *   <li>GRANT CONNECT ON DATABASE — разрешает подключение</li>
     *   <li>ALTER DEFAULT PRIVILEGES — новые таблицы автоматически доступны для чтения</li>
     * </ul>
     * </p>
     *
     * @param conn   соединение с БД (под ролью администратора)
     * @param dbName имя базы данных
     * @throws SQLException при ошибках выполнения
     */
    private void grantStudentAccess(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("GRANT CONNECT ON DATABASE " + dbName + " TO students");
        }
        try (Connection dbConn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
             Statement stmt = dbConn.createStatement()) {
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO students");
        }
    }

    /**
     * Читает содержимое загруженного SQL-файла в строку.
     *
     * @param filePart часть multipart-запроса с файлом
     * @return содержимое файла в виде строки
     * @throws IOException при ошибках чтения файла
     */
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

    private void executeSqlScriptWithLogs(Statement stmt, String script) throws SQLException {
        String[] lines = script.split("\n");
        StringBuilder currentQuery = new StringBuilder();
        boolean inBlockComment = false;
        int queryCount = 0;
        int totalQueries = 0;

        // Подсчёт запросов
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

    /**
     * Получает список всех пользовательских баз данных.
     *
     * @param conn соединение с БД (под ролью преподавателя)
     * @return список имён баз данных
     * @throws SQLException при ошибках выполнения запроса
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
     * Извлекает имя файла из заголовка Content-Disposition.
     * <p>
     * Поддерживает форматы как с полным путём (C:\folder\file.sql),
     * так и просто с именем файла (file.sql).
     * </p>
     *
     * @param part часть multipart-запроса
     * @return имя файла или null, если не найдено
     */
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
     * Удаляет указанную базу данных.
     * <p>
     * Перед удалением завершает все активные подключения к этой базе,
     * чтобы избежать ошибки "database is being accessed by other users".
     * </p>
     *
     * @param conn     соединение с БД (под ролью администратора)
     * @param dbName   имя удаляемой базы данных
     * @param response карта для формирования JSON-ответа
     */
    private void handleDelete(Connection conn, String dbName, Map<String, Object> response) {
        if (dbName == null || dbName.isEmpty() ||
                dbName.equals("postgres") || dbName.equals("template0") || dbName.equals("template1")) {
            response.put("error", "Cannot delete system database");
            return;
        }

        // Дополнительная проверка: только безопасные символы в имени БД
        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            response.put("error", "Invalid database name");
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            // Завершаем соединения с экранированием имени
            try {
                String terminateSql = String.format(
                        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
                                "WHERE datname = '%s' AND pid <> pg_backend_pid()",
                        dbName.replace("'", "''")
                );
                stmt.execute(terminateSql);
            } catch (SQLException e) {
                log.debug("No active connections to terminate for {}", dbName);
            }

            // Удаляем базу с экранированием имени
            String dropSql = String.format("DROP DATABASE IF EXISTS %s", dbName);
            stmt.executeUpdate(dropSql);
            response.put("success", true);
            response.put("message", "Database '" + dbName + "' deleted");

        } catch (SQLException e) {
            log.error("Failed to delete database: {}", dbName, e);
            response.put("error", "Failed to delete database: " + e.getMessage());
        }
    }
}