package com.sqltrainer.servlet.teacher;

import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.servlet.log.LogStreamServlet;
import com.sqltrainer.servlet.student.StudentServlet;
import com.sqltrainer.util.Constants;
import com.sqltrainer.util.QueryExecutor;
import com.google.gson.Gson;
import org.mindrot.jbcrypt.BCrypt;
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

    // Защищенные базы данных (только системные PostgreSQL)
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

            switch (action) {
                case "upload":
                    String dbName = req.getParameter("dbName");
                    String folderId = req.getParameter("folderId");
                    String displayName = req.getParameter("displayName");
                    String accessPassword = req.getParameter("accessPassword");
                    Part filePart = req.getPart("sqlFile");
                    handleUpload(conn, dbName, folderId, displayName, accessPassword, filePart, response);
                    break;

                case "uploadSchema":
                    String schemaDbName = req.getParameter("dbName");
                    Part imagePart = req.getPart("schemaImage");
                    handleUploadSchemaImage(conn, schemaDbName, imagePart, response);
                    break;

                case "list":
                    response.put("databases", getDatabaseList(conn));
                    break;

                case "delete":
                    String deleteDbName = req.getParameter("dbName");
                    handleDelete(conn, deleteDbName, response);
                    break;

                case "sessions":
                    Map<String, StudentServlet.SessionInfo> sessions = StudentServlet.getActiveSessions();
                    List<Map<String, Object>> sessionList = new ArrayList<>();
                    for (StudentServlet.SessionInfo info : sessions.values()) {
                        // Пропускаем сессии преподавателей - они не должны отображаться и не могут быть заблокированы
                        if (info.isTeacher()) {
                            continue;
                        }
                        Map<String, Object> sessionMap = new HashMap<>();
                        sessionMap.put("sessionId", info.getSessionId());
                        sessionMap.put("ipAddress", info.getIpAddress());
                        sessionMap.put("dbName", info.getDbName());
                        sessionMap.put("lastQuery", info.getLastQuery());
                        sessionMap.put("lastQueryTimeMs", info.getLastQueryTimeMs());
                        sessionMap.put("lastAccess", info.getLastAccess());
                        sessionMap.put("blocked", info.isBlocked());
                        sessionList.add(sessionMap);
                    }
                    response.put("sessions", sessionList);
                    break;

                case "terminateSession":
                    String terminateSessionId = req.getParameter("sessionId");
                    handleTerminateSession(terminateSessionId, response);
                    break;

                case "unblockSession":
                    String unblockSessionId = req.getParameter("sessionId");
                    handleUnblockSession(unblockSessionId, response);
                    break;

                case "createFolder":
                    String folderName = req.getParameter("folderName");
                    handleCreateFolder(conn, folderName, response);
                    break;

                case "listFolders":
                    response.put("folders", getFoldersList(conn));
                    break;

                case "updateDatabaseMetadata":
                    String updateDbName = req.getParameter("dbName");
                    String updateDisplayName = req.getParameter("displayName");
                    String updateFolderId = req.getParameter("folderId");
                    String updateAccessPassword = req.getParameter("accessPassword");
                    String isVisible = req.getParameter("isVisible");
                    String accessStart = req.getParameter("accessStart");
                    String accessEnd = req.getParameter("accessEnd");
                    handleUpdateDatabaseMetadata(conn, updateDbName, updateDisplayName, updateFolderId,
                            updateAccessPassword, isVisible, accessStart, accessEnd, response);
                    break;

                default:
                    response.put("error", "Unknown action: " + action);
                    log.warn("Unknown action received: {}", action);
                    break;
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
     * Создаёт новую папку для группировки баз данных.
     */
    private void handleCreateFolder(Connection conn, String folderName, Map<String, Object> response) {
        if (folderName == null || folderName.trim().isEmpty()) {
            response.put("error", "Folder name is required");
            return;
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO database_folders (name, owner_id) VALUES (?, ?)")) {
            stmt.setString(1, folderName.trim());
            stmt.setString(2, "teacher");
            stmt.executeUpdate();
            response.put("success", true);
            response.put("message", "Folder '" + folderName + "' created");
        } catch (SQLException e) {
            log.error("Failed to create folder: {}", e.getMessage());
            response.put("error", "Failed to create folder: " + e.getMessage());
        }
    }

    /**
     * Возвращает список папок.
     */
    private List<Map<String, Object>> getFoldersList(Connection conn) throws SQLException {
        List<Map<String, Object>> folders = new ArrayList<>();
        String sql = "SELECT id, name, owner_id, created_at FROM database_folders ORDER BY name";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> folder = new HashMap<>();
                folder.put("id", rs.getLong("id"));
                folder.put("name", rs.getString("name"));
                folder.put("ownerId", rs.getString("owner_id"));
                folder.put("createdAt", rs.getTimestamp("created_at"));
                folders.add(folder);
            }
        }
        return folders;
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
            // Запрещаем блокировать преподавателя
            if (info.isTeacher()) {
                response.put("error", "Cannot terminate teacher session");
                log.warn("Attempt to terminate teacher session: {}", sessionId);
                return;
            }
            info.setBlocked(true);
            response.put("success", true);
            response.put("message", "Session terminated: " + sessionId.substring(0, 8) + "...");
            log.info("Teacher terminated session: {} from IP {}", sessionId.substring(0, 8), info.getIpAddress());
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
     * Обновляет метаданные базы данных.
     */
    private void handleUpdateDatabaseMetadata(Connection conn, String dbName, String displayName,
                                              String folderId, String accessPassword, String isVisible,
                                              String accessStartStr, String accessEndStr,
                                              Map<String, Object> response) {
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }

        try {
            StringBuilder sql = new StringBuilder("UPDATE databases_metadata SET ");
            List<Object> params = new ArrayList<>();

            if (displayName != null && !displayName.isEmpty()) {
                sql.append("display_name = ?, ");
                params.add(displayName);
            }
            if (folderId != null && !folderId.isEmpty()) {
                sql.append("folder_id = ?, ");
                params.add(Long.parseLong(folderId));
            }
            if (accessPassword != null) {
                if (accessPassword.isEmpty()) {
                    sql.append("access_password_hash = NULL, ");
                } else {
                    sql.append("access_password_hash = ?, ");
                    params.add(BCrypt.hashpw(accessPassword, BCrypt.gensalt()));
                }
            }
            if (isVisible != null) {
                sql.append("is_visible = ?, ");
                params.add(Boolean.parseBoolean(isVisible));
            }
            if (accessStartStr != null && !accessStartStr.isEmpty()) {
                sql.append("access_start = ?::date, ");
                params.add(accessStartStr);
            }
            if (accessEndStr != null && !accessEndStr.isEmpty()) {
                sql.append("access_end = ?::date, ");
                params.add(accessEndStr);
            }

            // Если нет полей для обновления
            if (params.isEmpty()) {
                response.put("error", "No fields to update");
                return;
            }

            // Удаляем последнюю запятую и пробел
            sql.setLength(sql.length() - 2);
            sql.append(" WHERE db_name = ?");
            params.add(dbName);

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    response.put("success", true);
                    response.put("message", "Database metadata updated");
                    QueryExecutor.clearCache();
                } else {
                    response.put("error", "Database not found");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to update database metadata: {}", e.getMessage());
            response.put("error", "Failed to update metadata: " + e.getMessage());
        }
    }

    /**
     * Загружает схему базы данных (изображение).
     */
    private void handleUploadSchemaImage(Connection conn, String dbName, Part imagePart, Map<String, Object> response) throws SQLException {
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }

        if (imagePart == null || imagePart.getSize() == 0) {
            response.put("error", "Image file is required");
            return;
        }

        // Проверяем тип файла
        String contentType = imagePart.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            response.put("error", "Only image files are allowed (JPEG, PNG, GIF)");
            return;
        }

        // Проверяем размер файла (максимум 5MB)
        if (imagePart.getSize() > 5 * 1024 * 1024) {
            response.put("error", "Image file too large (max 5MB)");
            return;
        }

        // Определяем расширение файла
        String extension = "png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            extension = "jpg";
        } else if (contentType.contains("gif")) {
            extension = "gif";
        }

        // Генерируем уникальное имя файла
        String fileName = System.currentTimeMillis() + "_" + dbName + "." + extension;
        String uploadPath = getServletContext().getRealPath("/uploads/schemas/");
        java.io.File uploadDir = new java.io.File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        java.io.File destFile = new java.io.File(uploadDir, fileName);
        try {
            imagePart.write(destFile.getAbsolutePath());

            // Сохраняем путь в БД
            String imageUrl = "/uploads/schemas/" + fileName;
            String sql = "UPDATE databases_metadata SET schema_image_url = ? WHERE db_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, imageUrl);
                stmt.setString(2, dbName);
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    response.put("success", true);
                    response.put("imageUrl", imageUrl);
                    response.put("message", "Schema image uploaded successfully");
                    QueryExecutor.clearCache();
                } else {
                    response.put("error", "Database not found");
                }
            }
        } catch (IOException e) {
            log.error("Failed to upload schema image: {}", e.getMessage());
            response.put("error", "Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает загрузку SQL-скрипта и создание новой базы данных.
     */
    private void handleUpload(Connection conn, String dbName, String folderId, String displayName, String accessPassword,
                              Part filePart, Map<String, Object> response) throws IOException, SQLException {

        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }

        // Запрещаем создание системных баз PostgreSQL
        if (PROTECTED_DATABASES.contains(dbName.toLowerCase())) {
            response.put("error", "Cannot create database with system name: " + dbName);
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

        // 1. Создаём базу данных
        try (Statement stmt = conn.createStatement()) {
            String createSql = "CREATE DATABASE " + quotedDbName + " OWNER teacher_role";
            log.debug("Executing: {}", createSql);
            stmt.executeUpdate(createSql);
        } catch (SQLException e) {
            log.error("Failed to create database: {}", dbName, e);
            response.put("error", "Failed to create database: " + e.getMessage());
            return;
        }

        // 2. Выполняем SQL-скрипт в новой базе
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

        // 3. Даём права доступа
        try {
            grantAccessForNewDatabase(conn, quotedDbName, dbName);
        } catch (SQLException e) {
            log.warn("Could not grant access to {}: {}", dbName, e.getMessage());
        }

        // 4. Сохраняем метаданные базы данных
        if (folderId != null && !folderId.isEmpty()) {
            String passwordHash = null;
            if (accessPassword != null && !accessPassword.isEmpty()) {
                passwordHash = BCrypt.hashpw(accessPassword, BCrypt.gensalt());
            }

            String displayNameValue = displayName != null && !displayName.isEmpty() ? displayName : dbName;

            String metadataSql = "INSERT INTO databases_metadata (db_name, folder_id, display_name, access_password_hash, created_by) " +
                    "VALUES (?, ?, ?, ?, 'teacher')";
            try (PreparedStatement stmt = conn.prepareStatement(metadataSql)) {
                stmt.setString(1, dbName);
                stmt.setLong(2, Long.parseLong(folderId));
                stmt.setString(3, displayNameValue);
                stmt.setString(4, passwordHash);
                stmt.executeUpdate();
            } catch (SQLException e) {
                log.warn("Failed to insert database metadata: {}", e.getMessage());
            }
        }

        response.put("success", true);
        response.put("message", "Database '" + dbName + "' created successfully");
    }

    /**
     * Предоставляет доступ для студентов и преподавателя к созданной базе данных.
     */
    private void grantAccessForNewDatabase(Connection conn, String quotedDbName, String dbName) throws SQLException {
        // 1. Даём права на подключение студентам
        try (Statement stmt = conn.createStatement()) {
            String grantConnectSql = "GRANT CONNECT ON DATABASE " + quotedDbName + " TO students";
            log.debug("Executing: {}", grantConnectSql);
            stmt.executeUpdate(grantConnectSql);
        }

        // 2. Даём права на схему public
        try (Connection dbConn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
             Statement stmt = dbConn.createStatement()) {

            // Для студентов: только SELECT
            stmt.executeUpdate("GRANT SELECT ON ALL TABLES IN SCHEMA public TO students");
            stmt.executeUpdate("GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO students");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO students");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO students");

            // Для преподавателя: все права
            stmt.executeUpdate("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO teacher_role");
            stmt.executeUpdate("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO teacher_role");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO teacher_role");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO teacher_role");

            log.info("Access granted for database {}: students (SELECT), teacher_role (ALL)", dbName);
        }
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

    /**
     * Возвращает список баз данных для отображения студенту.
     * Берёт данные из таблицы databases_metadata, учитывая видимость и период доступа.
     */
    private List<Map<String, Object>> getDatabaseList(Connection conn) throws SQLException {
        List<Map<String, Object>> databases = new ArrayList<>();
        String sql = "SELECT dm.db_name, dm.display_name, df.name as folder_name, " +
                "dm.folder_id, dm.is_visible, dm.access_start, dm.access_end, " +
                "dm.schema_image_url, dm.access_password_hash " +
                "FROM databases_metadata dm " +
                "JOIN database_folders df ON dm.folder_id = df.id";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> db = new HashMap<>();
                db.put("dbName", rs.getString("db_name"));
                db.put("displayName", rs.getString("display_name"));
                db.put("folderName", rs.getString("folder_name"));
                db.put("folderId", rs.getLong("folder_id"));
                db.put("isVisible", rs.getBoolean("is_visible"));
                db.put("accessStart", rs.getDate("access_start"));
                db.put("accessEnd", rs.getDate("access_end"));
                db.put("schemaImageUrl", rs.getString("schema_image_url"));

                // Проверяем наличие пароля
                String passwordHash = rs.getString("access_password_hash");
                db.put("hasPassword", passwordHash != null && !passwordHash.isEmpty());

                databases.add(db);
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
     * Разблокирует сессию студента.
     */
    private void handleUnblockSession(String sessionId, Map<String, Object> response) {
        if (sessionId == null || sessionId.isEmpty()) {
            response.put("error", "Session ID is required");
            return;
        }

        Map<String, StudentServlet.SessionInfo> sessions = StudentServlet.getActiveSessions();
        StudentServlet.SessionInfo info = sessions.get(sessionId);

        if (info != null) {
            // Запрещаем разблокировать преподавателя (хотя их нет в списке)
            if (info.isTeacher()) {
                response.put("error", "Cannot unblock teacher session");
                log.warn("Attempt to unblock teacher session: {}", sessionId);
                return;
            }
            info.setBlocked(false);
            response.put("success", true);
            response.put("message", "Session unblocked: " + sessionId.substring(0, 8) + "...");
            log.info("Teacher unblocked session: {} from IP {}", sessionId.substring(0, 8), info.getIpAddress());
        } else {
            response.put("error", "Session not found");
            log.warn("Session not found for unblock: {}", sessionId);
        }
    }

    /**
     * Удаляет базу данных.
     * Преподаватель может удалить ЛЮБУЮ базу, кроме системных PostgreSQL.
     */
    private void handleDelete(Connection conn, String dbName, Map<String, Object> response) {
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Database name is required");
            return;
        }

        // Запрещаем удаление только системных баз PostgreSQL
        if (PROTECTED_DATABASES.contains(dbName.toLowerCase())) {
            response.put("error", "Cannot delete system database: " + dbName);
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