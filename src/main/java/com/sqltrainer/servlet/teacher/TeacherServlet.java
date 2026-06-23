package com.sqltrainer.servlet.teacher;

import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.servlet.log.LogStreamServlet;
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
 *
 * Функциональность:
 * - Создание новых БД из SQL-скриптов
 * - Загрузка схемы БД (изображение)
 * - Удаление существующих БД
 * - Управление папками (категориями): создание, редактирование, удаление
 * - Перемещение баз данных между папками
 * - Управление метаданными БД (видимость, пароль, период доступа, лимит строк)
 * - Генерация вопросов для Moodle
 */
@WebServlet("/api/teacher")
@MultipartConfig(
        maxFileSize = Constants.MAX_FILE_SIZE_BYTES,
        maxRequestSize = Constants.MAX_REQUEST_SIZE_BYTES
)
public class TeacherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(TeacherServlet.class);
    private final Gson gson = new Gson();

    // Разрешённые расширения файлов для загрузки SQL
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(Constants.ALLOWED_SQL_EXTENSIONS));

    // Паттерн для валидации имени базы данных (только латиница, цифры, подчёркивание)
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // Защищённые базы данных (системные PostgreSQL) — нельзя удалить
    private static final Set<String> PROTECTED_DATABASES = new HashSet<>(Arrays.asList(Constants.PROTECTED_DATABASES));

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        Map<String, Object> response = new HashMap<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, "postgres")) {

            switch (action) {
                // ========== УПРАВЛЕНИЕ БАЗАМИ ДАННЫХ ==========

                case "upload":
                    // Загрузка SQL-скрипта и создание новой БД
                    String dbName = req.getParameter("dbName");
                    String folderId = req.getParameter("folderId");
                    String displayName = req.getParameter("displayName");
                    String accessPassword = req.getParameter("accessPassword");
                    String maxRows = req.getParameter("maxRows");
                    Part filePart = req.getPart("sqlFile");
                    handleUpload(conn, dbName, folderId, displayName, accessPassword, maxRows, filePart, response);
                    break;

                case "uploadSchema":
                    // Загрузка схемы БД (изображение со связями таблиц)
                    String schemaDbName = req.getParameter("dbName");
                    Part imagePart = req.getPart("schemaImage");
                    handleUploadSchemaImage(conn, schemaDbName, imagePart, response);
                    break;

                case "list":
                    // Получение списка ВСЕХ БД для преподавателя (включая проблемные)
                    response.put("databases", getDatabaseList(conn));
                    break;

                case "listNormalDatabases":
                    // Получение списка только НОРМАЛЬНЫХ БД (с существующей папкой)
                    response.put("databases", getNormalDatabaseList(conn));
                    break;

                case "delete":
                    // Удаление БД (с проверкой защиты системных БД)
                    String deleteDbName = req.getParameter("dbName");
                    handleDelete(conn, deleteDbName, response);
                    break;

                // ========== УПРАВЛЕНИЕ ПАПКАМИ ==========

                case "createFolder":
                    // Создание новой папки
                    String folderName = req.getParameter("folderName");
                    handleCreateFolder(conn, folderName, response);
                    break;

                case "listFolders":
                    // Получение списка всех папок
                    response.put("folders", getFoldersList(conn));
                    break;

                case "updateFolder":
                    // Изменение названия папки
                    String folderIdToUpdate = req.getParameter("folderId");
                    String newFolderName = req.getParameter("folderName");
                    handleUpdateFolder(conn, folderIdToUpdate, newFolderName, response);
                    break;

                case "deleteFolder":
                    // Удаление папки (только если в ней нет баз данных)
                    String folderIdToDelete = req.getParameter("folderId");
                    handleDeleteFolder(conn, folderIdToDelete, response);
                    break;

                case "moveDatabaseToFolder":
                    // Перемещение базы данных в другую папку
                    String dbNameToMove = req.getParameter("dbName");
                    String targetFolderId = req.getParameter("targetFolderId");
                    handleMoveDatabaseToFolder(conn, dbNameToMove, targetFolderId, response);
                    break;

                // ========== РЕДАКТИРОВАНИЕ МЕТАДАННЫХ ==========

                case "updateDatabaseMetadata":
                    String updateDbName = req.getParameter("dbName");
                    String updateDisplayName = req.getParameter("displayName");
                    String updateFolderId = req.getParameter("folderId");
                    String updateAccessPassword = req.getParameter("accessPassword");
                    String removePassword = req.getParameter("removePassword");
                    String isVisible = req.getParameter("isVisible");
                    String accessStart = req.getParameter("accessStart");
                    String accessEnd = req.getParameter("accessEnd");
                    String updateMaxRows = req.getParameter("maxRows");
                    handleUpdateDatabaseMetadata(conn, updateDbName, updateDisplayName, updateFolderId,
                            updateAccessPassword, isVisible, accessStart, accessEnd, removePassword, updateMaxRows, response);
                    break;

                // ========== НЕИЗВЕСТНОЕ ДЕЙСТВИЕ ==========

                default:
                    response.put("error", "Неизвестное действие: " + action);
                    log.warn("Unknown action received: {}", action);
                    break;
            }

        } catch (Exception e) {
            log.error("Teacher action failed: {}", action, e);
            response.put("error", "Ошибка: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(response));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // GET-запросы обрабатываются так же, как POST
        doPost(req, resp);
    }

    // ============================================
    // УПРАВЛЕНИЕ ПАПКАМИ
    // ============================================

    /**
     * Создаёт новую папку для группировки баз данных.
     *
     * @param conn       соединение с БД
     * @param folderName название папки
     * @param response   объект для формирования ответа
     */
    private void handleCreateFolder(Connection conn, String folderName, Map<String, Object> response) {
        if (folderName == null || folderName.trim().isEmpty()) {
            response.put("error", "Не указано имя папки");
            return;
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO database_folders (name, owner_id) VALUES (?, ?)")) {
            stmt.setString(1, folderName.trim());
            stmt.setString(2, "teacher");
            stmt.executeUpdate();
            response.put("success", true);
            response.put("message", "Папка '" + folderName + "' создана");
            log.info("Folder created: {}", folderName);
        } catch (SQLException e) {
            log.error("Failed to create folder: {}", e.getMessage());
            response.put("error", "Ошибка создания папки: " + e.getMessage());
        }
    }

    /**
     * Возвращает список всех папок.
     *
     * @param conn соединение с БД
     * @return список папок
     * @throws SQLException при ошибке выполнения запроса
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
     * Обновляет название папки.
     * Проверяет, что новое имя не конфликтует с существующими папками.
     *
     * @param conn          соединение с БД
     * @param folderIdStr   идентификатор папки
     * @param newName       новое название папки
     * @param response      объект для формирования ответа
     */
    private void handleUpdateFolder(Connection conn, String folderIdStr, String newName, Map<String, Object> response) {
        if (folderIdStr == null || folderIdStr.isEmpty()) {
            response.put("error", "Не указан ID папки");
            return;
        }

        if (newName == null || newName.trim().isEmpty()) {
            response.put("error", "Название папки не может быть пустым");
            return;
        }

        try {
            long folderId = Long.parseLong(folderIdStr);

            // Проверяем, существует ли папка с таким именем (исключая текущую)
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM database_folders WHERE name = ? AND id != ?")) {
                checkStmt.setString(1, newName.trim());
                checkStmt.setLong(2, folderId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    response.put("error", "Папка с таким именем уже существует");
                    return;
                }
            }

            // Обновляем название папки
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE database_folders SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                stmt.setString(1, newName.trim());
                stmt.setLong(2, folderId);
                int updated = stmt.executeUpdate();

                if (updated > 0) {
                    response.put("success", true);
                    response.put("message", "Название папки обновлено");
                    QueryExecutor.clearCache();
                    log.info("Folder renamed: id={}, newName={}", folderId, newName);
                } else {
                    response.put("error", "Папка не найдена");
                }
            }
        } catch (NumberFormatException e) {
            response.put("error", "Неверный формат ID папки");
        } catch (SQLException e) {
            log.error("Failed to update folder: {}", e.getMessage());
            response.put("error", "Ошибка обновления папки: " + e.getMessage());
        }
    }

    /**
     * Удаляет папку. Удаление возможно только если в папке нет баз данных.
     * Если в папке есть базы данных, операция отклоняется с соответствующим сообщением.
     *
     * @param conn            соединение с БД
     * @param folderIdStr     идентификатор папки
     * @param response        объект для формирования ответа
     */
    private void handleDeleteFolder(Connection conn, String folderIdStr, Map<String, Object> response) {
        if (folderIdStr == null || folderIdStr.isEmpty()) {
            response.put("error", "Не указан ID папки");
            return;
        }

        try {
            long folderId = Long.parseLong(folderIdStr);

            // Проверяем, есть ли базы данных в этой папке
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM databases_metadata WHERE folder_id = ?")) {
                checkStmt.setLong(1, folderId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    int dbCount = rs.getInt(1);
                    response.put("error", "Невозможно удалить папку: в ней находится " + dbCount +
                            " баз(ы) данных. Сначала переместите или удалите их.");
                    return;
                }
            }

            // Удаляем папку
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM database_folders WHERE id = ?")) {
                stmt.setLong(1, folderId);
                int deleted = stmt.executeUpdate();

                if (deleted > 0) {
                    response.put("success", true);
                    response.put("message", "Папка удалена");
                    QueryExecutor.clearCache();
                    log.info("Folder deleted: id={}", folderId);
                } else {
                    response.put("error", "Папка не найдена");
                }
            }
        } catch (NumberFormatException e) {
            response.put("error", "Неверный формат ID папки");
        } catch (SQLException e) {
            log.error("Failed to delete folder: {}", e.getMessage());
            response.put("error", "Ошибка удаления папки: " + e.getMessage());
        }
    }

    /**
     * Перемещает базу данных в другую папку.
     * Проверяет существование целевой папки и базы данных перед перемещением.
     *
     * @param conn                соединение с БД
     * @param dbName              имя базы данных
     * @param targetFolderIdStr   идентификатор целевой папки
     * @param response            объект для формирования ответа
     */
    private void handleMoveDatabaseToFolder(Connection conn, String dbName, String targetFolderIdStr,
                                            Map<String, Object> response) {
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Не указано имя базы данных");
            return;
        }

        if (targetFolderIdStr == null || targetFolderIdStr.isEmpty()) {
            response.put("error", "Не указана целевая папка");
            return;
        }

        try {
            long targetFolderId = Long.parseLong(targetFolderIdStr);

            // Проверяем, существует ли целевая папка
            try (PreparedStatement checkFolderStmt = conn.prepareStatement(
                    "SELECT id FROM database_folders WHERE id = ?")) {
                checkFolderStmt.setLong(1, targetFolderId);
                if (!checkFolderStmt.executeQuery().next()) {
                    response.put("error", "Целевая папка не существует");
                    return;
                }
            }

            // Проверяем, существует ли база данных
            try (PreparedStatement checkDbStmt = conn.prepareStatement(
                    "SELECT db_name FROM databases_metadata WHERE db_name = ?")) {
                checkDbStmt.setString(1, dbName);
                if (!checkDbStmt.executeQuery().next()) {
                    response.put("error", "База данных не найдена");
                    return;
                }
            }

            // Перемещаем базу в другую папку
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE databases_metadata SET folder_id = ?, updated_at = CURRENT_TIMESTAMP WHERE db_name = ?")) {
                stmt.setLong(1, targetFolderId);
                stmt.setString(2, dbName);
                int updated = stmt.executeUpdate();

                if (updated > 0) {
                    response.put("success", true);
                    response.put("message", "База данных перемещена в папку");
                    QueryExecutor.clearCache();
                    log.info("Database moved: dbName={}, newFolderId={}", dbName, targetFolderId);
                } else {
                    response.put("error", "Не удалось переместить базу данных");
                }
            }
        } catch (NumberFormatException e) {
            response.put("error", "Неверный формат ID папки");
        } catch (SQLException e) {
            log.error("Failed to move database: {}", e.getMessage());
            response.put("error", "Ошибка перемещения базы данных: " + e.getMessage());
        }
    }

    // ============================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (экранирование, валидация)
    // ============================================

    /**
     * Экранирует идентификатор для безопасного использования в SQL.
     * Использует встроенную функцию PostgreSQL quote_ident для защиты от SQL-инъекций.
     *
     * @param conn       соединение с БД
     * @param identifier идентификатор для экранирования (имя БД, таблицы, колонки)
     * @return экранированный идентификатор
     * @throws SQLException при ошибке выполнения запроса
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

    // ============================================
    // РЕДАКТИРОВАНИЕ МЕТАДАННЫХ БАЗЫ ДАННЫХ
    // ============================================

    /**
     * Обновляет метаданные базы данных.
     *
     * Позволяет изменить:
     * - отображаемое имя
     * - папку
     * - пароль доступа (или удалить его)
     * - видимость для студентов
     * - период доступа (даты начала и окончания)
     * - максимальное количество строк для студентов (max_rows)
     *
     * @param conn               соединение с БД
     * @param dbName             имя базы данных
     * @param displayName        новое отображаемое имя
     * @param folderId           новый идентификатор папки
     * @param accessPassword     новый пароль (если указан)
     * @param isVisible          видимость для студентов ("true"/"false")
     * @param accessStartStr     дата начала доступа
     * @param accessEndStr       дата окончания доступа
     * @param removePasswordParam флаг удаления пароля
     * @param maxRows            новое максимальное количество строк
     * @param response           объект для ответа клиенту
     */
    private void handleUpdateDatabaseMetadata(Connection conn, String dbName, String displayName,
                                              String folderId, String accessPassword, String isVisible,
                                              String accessStartStr, String accessEndStr,
                                              String removePasswordParam, String maxRows,
                                              Map<String, Object> response) {
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Не указано имя базы данных");
            return;
        }

        try {
            StringBuilder sql = new StringBuilder("UPDATE databases_metadata SET ");
            List<Object> params = new ArrayList<>();

            // Обновление отображаемого имени
            if (displayName != null && !displayName.isEmpty()) {
                sql.append("display_name = ?, ");
                params.add(displayName);
            }

            // Обновление папки (обязательно)
            if (folderId != null && !folderId.isEmpty()) {
                sql.append("folder_id = ?, ");
                params.add(Long.parseLong(folderId));
            } else {
                response.put("error", "Папка обязательна для выбора");
                return;
            }

            // Обновление пароля: удаление существующего или установка нового
            if ("true".equals(removePasswordParam)) {
                sql.append("access_password_hash = NULL, ");
            } else if (accessPassword != null && !accessPassword.isEmpty()) {
                sql.append("access_password_hash = ?, ");
                params.add(BCrypt.hashpw(accessPassword, BCrypt.gensalt()));
            }

            // Обновление видимости
            if (isVisible != null) {
                sql.append("is_visible = ?, ");
                params.add(Boolean.parseBoolean(isVisible));
            }

            // Обновление даты начала доступа
            if (accessStartStr != null && !accessStartStr.isEmpty()) {
                sql.append("access_start = ?::date, ");
                params.add(accessStartStr);
            }

            // Обновление даты окончания доступа
            if (accessEndStr != null && !accessEndStr.isEmpty()) {
                sql.append("access_end = ?::date, ");
                params.add(accessEndStr);
            }

            // Обновление лимита строк для студентов
            if (maxRows != null && !maxRows.isEmpty()) {
                sql.append("max_rows = ?, ");
                params.add(Integer.parseInt(maxRows));
            }

            if (params.isEmpty()) {
                response.put("error", "Нет полей для обновления");
                return;
            }

            // Удаляем последнюю запятую и пробел, добавляем WHERE условие
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
                    response.put("message", "Метаданные базы данных обновлены");
                    QueryExecutor.clearCache();
                } else {
                    response.put("error", "База данных не найдена");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to update database metadata: {}", e.getMessage());
            response.put("error", "Ошибка обновления метаданных: " + e.getMessage());
        }
    }

    // ============================================
    // ЗАГРУЗКА СХЕМЫ БАЗЫ ДАННЫХ
    // ============================================

    /**
     * Загружает схему базы данных (изображение со связями таблиц).
     * Изображение сохраняется в файловую систему, в БД сохраняется только путь.
     * При загрузке нового изображения старое удаляется.
     *
     * @param conn      соединение с БД
     * @param dbName    имя базы данных
     * @param imagePart файл изображения (JPEG, PNG, GIF)
     * @param response  объект для формирования ответа клиенту
     */
    private void handleUploadSchemaImage(Connection conn, String dbName, Part imagePart, Map<String, Object> response) {
        // Валидация имени БД
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Не указано имя базы данных");
            return;
        }

        // Валидация файла
        if (imagePart == null || imagePart.getSize() == 0) {
            response.put("error", "Не указан файл изображения");
            return;
        }

        // Проверка MIME-типа (только изображения)
        String contentType = imagePart.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            response.put("error", "Разрешены только файлы изображений (JPEG, PNG, GIF)");
            return;
        }

        // Ограничение размера (5 МБ)
        if (imagePart.getSize() > 5 * 1024 * 1024) {
            response.put("error", "Файл изображения слишком большой (максимум 5 МБ)");
            return;
        }

        // Определение расширения файла по MIME-типу
        String extension = "png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            extension = "jpg";
        } else if (contentType.contains("gif")) {
            extension = "gif";
        }

        // Генерация уникального имени файла
        String fileName = System.currentTimeMillis() + "_" + dbName + "." + extension;
        String uploadPath = "/usr/local/tomcat/uploads/schemas/";
        java.io.File uploadDir = new java.io.File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        java.io.File destFile = new java.io.File(uploadDir, fileName);
        try {
            // Сохраняем файл на диск
            imagePart.write(destFile.getAbsolutePath());
            log.info("Image saved to: {}", destFile.getAbsolutePath());

            String imageUrl = "/uploads/schemas/" + fileName;

            // Удаляем старое изображение, если существует
            String checkSql = "SELECT schema_image_url FROM databases_metadata WHERE db_name = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, dbName);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    String oldUrl = rs.getString("schema_image_url");
                    if (oldUrl != null && !oldUrl.isEmpty()) {
                        String oldFileName = oldUrl.substring(oldUrl.lastIndexOf('/') + 1);
                        java.io.File oldFile = new java.io.File(uploadDir, oldFileName);
                        if (oldFile.exists()) {
                            oldFile.delete();
                            log.info("Deleted old image: {}", oldFileName);
                        }
                    }
                }
            }

            // Обновляем URL схемы в метаданных
            String sql = "UPDATE databases_metadata SET schema_image_url = ? WHERE db_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, imageUrl);
                stmt.setString(2, dbName);
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    response.put("success", true);
                    response.put("imageUrl", imageUrl);
                    response.put("message", "Схема базы данных успешно загружена");
                    QueryExecutor.clearCache();
                } else {
                    response.put("error", "База данных не найдена");
                }
            }
        } catch (IOException e) {
            log.error("Failed to upload schema image: {}", e.getMessage());
            response.put("error", "Ошибка загрузки изображения: " + e.getMessage());
        } catch (SQLException e) {
            log.error("Database error while uploading schema image: {}", e.getMessage());
            response.put("error", "Ошибка базы данных: " + e.getMessage());
        }
    }

    // ============================================
    // ЗАГРУЗКА SQL-СКРИПТА И СОЗДАНИЕ БД
    // ============================================

    /**
     * Обрабатывает загрузку SQL-скрипта и создание новой базы данных.
     *
     * Процесс:
     * 1. Валидация имени БД, файла и содержимого
     * 2. Создание физической БД в PostgreSQL
     * 3. Выполнение SQL-скрипта с логированием прогресса через SSE
     * 4. Настройка прав доступа для ролей students и teacher_role
     * 5. Сохранение метаданных в таблицу databases_metadata (включая max_rows)
     *
     * @param conn           соединение с БД
     * @param dbName         имя новой базы данных
     * @param folderId       идентификатор папки (обязательный)
     * @param displayName    отображаемое имя
     * @param accessPassword пароль доступа
     * @param maxRows        максимальное количество строк для студентов (по умолчанию 20)
     * @param filePart       SQL файл
     * @param response       объект для формирования ответа
     * @throws IOException  при ошибке чтения файла
     * @throws SQLException при ошибке выполнения SQL
     */
    private void handleUpload(Connection conn, String dbName, String folderId, String displayName,
                              String accessPassword, String maxRows, Part filePart, Map<String, Object> response)
            throws IOException, SQLException {

        // ===== ВАЛИДАЦИЯ =====

        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Не указано имя базы данных");
            return;
        }

        // Запрещаем создание БД с системными именами PostgreSQL
        if (PROTECTED_DATABASES.contains(dbName.toLowerCase())) {
            response.put("error", "Нельзя создать базу с системным именем: " + dbName);
            return;
        }

        // Проверка имени БД (только латиница, цифры, подчёркивание)
        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            response.put("error", "Имя базы данных может содержать только буквы, цифры и подчёркивания");
            return;
        }

        // Максимальная длина имени БД в PostgreSQL — 63 символа
        if (dbName.length() > Constants.MAX_DB_NAME_LENGTH) {
            response.put("error", "Имя базы данных слишком длинное (максимум 63 символа)");
            return;
        }

        // Проверка наличия файла
        if (filePart == null || filePart.getSize() == 0) {
            response.put("error", "Не указан SQL файл");
            return;
        }

        // Проверка расширения файла (.sql)
        String fileName = getFileName(filePart);
        if (!isValidFileExtension(fileName)) {
            response.put("error", "Разрешены только .sql файлы");
            return;
        }

        // Чтение и проверка содержимого SQL-скрипта
        String script = readSqlScript(filePart);
        if (script.length() > Constants.MAX_SCRIPT_SIZE_BYTES) {
            response.put("error", "SQL скрипт слишком большой (максимум 2 МБ)");
            return;
        }
        if (!isValidSqlContent(script)) {
            response.put("error", "Неверное содержимое SQL. Скрипт должен содержать CREATE TABLE или INSERT");
            return;
        }

        // Проверка существования БД с таким именем
        if (databaseExists(conn, dbName)) {
            response.put("error", "База данных с именем '" + dbName + "' уже существует");
            return;
        }

        // ===== ПРОВЕРКА ПАПКИ (ОБЯЗАТЕЛЬНАЯ) =====
        if (folderId == null || folderId.isEmpty()) {
            response.put("error", "Не выбрана папка для базы данных");
            return;
        }

        long folderIdLong;
        try {
            folderIdLong = Long.parseLong(folderId);
        } catch (NumberFormatException e) {
            response.put("error", "Неверный формат идентификатора папки");
            return;
        }

        // Проверяем существование выбранной папки
        try (PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM database_folders WHERE id = ?")) {
            checkStmt.setLong(1, folderIdLong);
            if (!checkStmt.executeQuery().next()) {
                response.put("error", "Выбранная папка не существует");
                return;
            }
        }

        // ===== СОЗДАНИЕ БАЗЫ ДАННЫХ =====

        QueryExecutor.clearCache();
        log.info("Cache cleared before creating database: {}", dbName);

        String quotedDbName = quoteIdent(conn, dbName);

        // Создаём физическую БД с владельцем teacher_role
        try (Statement stmt = conn.createStatement()) {
            String createSql = "CREATE DATABASE " + quotedDbName + " OWNER teacher_role";
            log.debug("Executing: {}", createSql);
            stmt.executeUpdate(createSql);
        } catch (SQLException e) {
            log.error("Failed to create database: {}", dbName, e);
            response.put("error", "Ошибка создания базы данных: " + e.getMessage());
            return;
        }

        // ===== ВЫПОЛНЕНИЕ SQL-СКРИПТА =====

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
                response.put("error", "Ошибка выполнения SQL скрипта: " + e.getMessage());
                return;
            }
        }

        // ===== НАСТРОЙКА ПРАВ ДОСТУПА =====

        try {
            grantAccessForNewDatabase(conn, quotedDbName, dbName);
        } catch (SQLException e) {
            log.warn("Could not grant access to {}: {}", dbName, e.getMessage());
        }

        // ===== СОХРАНЕНИЕ МЕТАДАННЫХ =====

        // Хешируем пароль, если он указан
        String passwordHash = null;
        if (accessPassword != null && !accessPassword.isEmpty()) {
            passwordHash = BCrypt.hashpw(accessPassword, BCrypt.gensalt());
        }

        String displayNameValue = displayName != null && !displayName.isEmpty() ? displayName : dbName;

        // Устанавливаем лимит строк (по умолчанию 20)
        int maxRowsValue = 20;
        if (maxRows != null && !maxRows.isEmpty()) {
            try {
                maxRowsValue = Integer.parseInt(maxRows);
                if (maxRowsValue < 1) maxRowsValue = 20;
            } catch (NumberFormatException e) {
                maxRowsValue = 20;
            }
        }

        String metadataSql = "INSERT INTO databases_metadata (db_name, folder_id, display_name, access_password_hash, max_rows, created_by) " +
                "VALUES (?, ?, ?, ?, ?, 'teacher')";

        try (PreparedStatement stmt = conn.prepareStatement(metadataSql)) {
            stmt.setString(1, dbName);
            stmt.setLong(2, folderIdLong);  // Обязательно, не NULL
            stmt.setString(3, displayNameValue);

            if (passwordHash != null) {
                stmt.setString(4, passwordHash);
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }

            stmt.setInt(5, maxRowsValue);
            stmt.executeUpdate();

            log.info("Database metadata saved for: {} (folder: {})", dbName, folderIdLong);

        } catch (SQLException e) {
            log.warn("Failed to insert database metadata: {}", e.getMessage());
            response.put("error", "Ошибка сохранения метаданных базы данных: " + e.getMessage());
            return;
        }

        response.put("success", true);
        response.put("message", "База данных '" + dbName + "' успешно создана");
        log.info("Database created successfully: {}", dbName);
    }

    /**
     * Предоставляет доступ для студентов и преподавателя к созданной базе данных.
     *
     * Права доступа:
     * - students: только SELECT на все таблицы
     * - teacher_role: полные права (ALL PRIVILEGES)
     *
     * @param conn          соединение с БД (системная БД postgres)
     * @param quotedDbName  экранированное имя базы данных
     * @param dbName        имя базы данных
     * @throws SQLException при ошибке выполнения запроса
     */
    private void grantAccessForNewDatabase(Connection conn, String quotedDbName, String dbName) throws SQLException {
        // Даём право подключения к БД для роли students
        try (Statement stmt = conn.createStatement()) {
            String grantConnectSql = "GRANT CONNECT ON DATABASE " + quotedDbName + " TO students";
            log.debug("Executing: {}", grantConnectSql);
            stmt.executeUpdate(grantConnectSql);
        }

        // Настраиваем права внутри новой БД
        try (Connection dbConn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, dbName);
             Statement stmt = dbConn.createStatement()) {

            // Для студентов: только SELECT
            stmt.executeUpdate("GRANT SELECT ON ALL TABLES IN SCHEMA public TO students");
            stmt.executeUpdate("GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO students");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO students");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO students");

            // Для преподавателя: полные права
            stmt.executeUpdate("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO teacher_role");
            stmt.executeUpdate("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO teacher_role");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO teacher_role");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO teacher_role");

            log.info("Access granted for database {}: students (SELECT), teacher_role (ALL)", dbName);
        }
    }

    // ============================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ЗАГРУЗКИ SQL
    // ============================================

    /**
     * Проверяет, является ли расширение файла допустимым (.sql).
     *
     * @param fileName имя файла
     * @return true если расширение допустимо
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
     *
     * @param content содержимое скрипта
     * @return true если скрипт безопасен и содержит CREATE TABLE или INSERT
     */
    private boolean isValidSqlContent(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String lower = content.toLowerCase();

        // Запрещаем опасные команды
        for (String cmd : Constants.DANGEROUS_SCRIPT_PATTERNS) {
            if (lower.contains(cmd)) return false;
        }

        // Должен содержать CREATE TABLE или INSERT
        return lower.contains("create table") ||
                lower.contains("insert into") ||
                lower.contains("create database");
    }

    /**
     * Проверяет, существует ли база данных с указанным именем.
     *
     * @param conn   соединение с БД
     * @param dbName имя базы данных
     * @return true если база существует
     * @throws SQLException при ошибке выполнения запроса
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
     * Удаляет базу данных (используется при откате в случае ошибки).
     *
     * @param conn           соединение с БД
     * @param quotedDbName   экранированное имя базы данных
     * @param originalDbName оригинальное имя базы данных
     * @throws SQLException при ошибке выполнения запроса
     */
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
     * Читает содержимое SQL файла.
     *
     * @param filePart файл
     * @return содержимое файла в виде строки
     * @throws IOException при ошибке чтения файла
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

    /**
     * Выполняет SQL-скрипт и отправляет прогресс через SSE (Server-Sent Events).
     *
     * Логирование в реальном времени:
     * - type: "start", "progress", "success", "error", "complete"
     *
     * @param stmt   SQL statement
     * @param script содержимое скрипта
     * @throws SQLException при ошибке выполнения запроса
     */
    private void executeSqlScriptWithLogs(Statement stmt, String script) throws SQLException {
        String[] lines = script.split("\n");
        StringBuilder currentQuery = new StringBuilder();
        boolean inBlockComment = false;
        int queryCount = 0;
        int totalQueries = 0;

        // Подсчитываем общее количество запросов
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--") &&
                    !trimmed.startsWith("/*") && trimmed.endsWith(";")) {
                totalQueries++;
            }
        }

        if (totalQueries == 0) totalQueries = 100; // fallback

        log.info("Starting SQL script execution. Total queries: {}", totalQueries);
        LogStreamServlet.addLog("{\"type\":\"start\",\"total\":" + totalQueries + "}");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Обработка многострочных комментариев /* ... */
            if (trimmed.startsWith("/*")) inBlockComment = true;
            if (inBlockComment) {
                if (trimmed.contains("*/")) inBlockComment = false;
                continue;
            }
            // Пропускаем однострочные комментарии --
            if (trimmed.startsWith("--")) continue;

            currentQuery.append(line).append(" ");

            // Если строка заканчивается на ';' — выполняем запрос
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
                                "{\"type\":\"success\",\"current\":%d,\"message\":\"Запрос %d выполнен\"}",
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
        LogStreamServlet.addLog("{\"type\":\"complete\",\"message\":\"Выполнение скрипта завершено\"}");
    }

    /**
     * Возвращает список ВСЕХ баз данных для отображения преподавателю в разделе "Существующие базы данных".
     * Использует LEFT JOIN, чтобы показать даже проблемные базы (без папки или с битой папкой).
     *
     * @param conn соединение с БД
     * @return список всех баз данных с метаданными
     * @throws SQLException при ошибке выполнения запроса
     */
    private List<Map<String, Object>> getDatabaseList(Connection conn) throws SQLException {
        List<Map<String, Object>> databases = new ArrayList<>();
        String sql = "SELECT dm.db_name, dm.display_name, df.name as folder_name, " +
                "dm.folder_id, dm.is_visible, dm.access_start, dm.access_end, " +
                "dm.schema_image_url, dm.access_password_hash, dm.max_rows " +
                "FROM databases_metadata dm " +
                "LEFT JOIN database_folders df ON dm.folder_id = df.id";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> db = new HashMap<>();
                db.put("dbName", rs.getString("db_name"));
                db.put("displayName", rs.getString("display_name"));
                db.put("folderName", rs.getString("folder_name") != null ? rs.getString("folder_name") : "(нет папки)");
                db.put("folderId", rs.getObject("folder_id") != null ? rs.getLong("folder_id") : null);
                db.put("isVisible", rs.getBoolean("is_visible"));
                db.put("accessStart", rs.getDate("access_start"));
                db.put("accessEnd", rs.getDate("access_end"));
                db.put("schemaImageUrl", rs.getString("schema_image_url"));
                db.put("maxRows", rs.getInt("max_rows"));

                String passwordHash = rs.getString("access_password_hash");
                db.put("hasPassword", passwordHash != null && !passwordHash.isEmpty());

                databases.add(db);
            }
        }
        return databases;
    }

    /**
     * Возвращает список ТОЛЬКО НОРМАЛЬНЫХ баз данных для выпадающих списков
     * (генерация вопросов Moodle, загрузка схемы).
     * Использует INNER JOIN и проверку folder_id IS NOT NULL.
     *
     * @param conn соединение с БД
     * @return список нормальных баз данных
     * @throws SQLException при ошибке выполнения запроса
     */
    private List<Map<String, Object>> getNormalDatabaseList(Connection conn) throws SQLException {
        List<Map<String, Object>> databases = new ArrayList<>();
        // INNER JOIN + явная проверка folder_id IS NOT NULL
        String sql = "SELECT dm.db_name, dm.display_name, df.name as folder_name, " +
                "dm.folder_id, dm.is_visible, dm.access_start, dm.access_end, " +
                "dm.schema_image_url, dm.access_password_hash, dm.max_rows " +
                "FROM databases_metadata dm " +
                "INNER JOIN database_folders df ON dm.folder_id = df.id " +
                "WHERE dm.folder_id IS NOT NULL " +
                "ORDER BY df.name, dm.display_name";

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
                db.put("maxRows", rs.getInt("max_rows"));

                String passwordHash = rs.getString("access_password_hash");
                db.put("hasPassword", passwordHash != null && !passwordHash.isEmpty());

                databases.add(db);
            }
        }
        return databases;
    }

    /**
     * Извлекает имя файла из заголовка Content-Disposition.
     *
     * @param part файл
     * @return имя файла
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

    // ============================================
    // УДАЛЕНИЕ БАЗЫ ДАННЫХ
    // ============================================

    /**
     * Удаляет базу данных.
     * Преподаватель может удалить любую базу, кроме системных PostgreSQL.
     *
     * Процесс удаления:
     * 1. Закрываем пулы соединений HikariCP
     * 2. Принудительно завершаем все активные подключения к БД
     * 3. Выполняем DROP DATABASE
     * 4. Удаляем запись из databases_metadata
     *
     * @param conn     соединение с БД
     * @param dbName   имя базы данных
     * @param response объект для формирования ответа
     */
    private void handleDelete(Connection conn, String dbName, Map<String, Object> response) {
        // Валидация
        if (dbName == null || dbName.isEmpty()) {
            response.put("error", "Не указано имя базы данных");
            return;
        }

        // Запрещаем удаление системных баз PostgreSQL
        if (PROTECTED_DATABASES.contains(dbName.toLowerCase())) {
            response.put("error", "Нельзя удалить системную базу данных: " + dbName);
            return;
        }

        // Проверка имени БД
        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            response.put("error", "Недопустимое имя базы данных");
            return;
        }

        try {
            // Очищаем кэш запросов
            QueryExecutor.clearCache();
            log.info("Cache cleared before deleting database: {}", dbName);

            // 1. Закрываем пулы соединений
            DatabaseConfig.closeStudentPool(dbName);
            DatabaseConfig.closeTeacherPool(dbName);
            log.info("Closed connection pools for database: {}", dbName);

            try (Statement stmt = conn.createStatement()) {
                // 2. Принудительно завершаем все подключения к базе
                String terminateSql = String.format(
                        "SELECT pg_terminate_backend(pid) " +
                                "FROM pg_stat_activity " +
                                "WHERE datname = '%s'",
                        dbName
                );
                log.info("Terminating all connections to {} with query: {}", dbName, terminateSql);

                try {
                    int terminated = stmt.executeUpdate(terminateSql);
                    log.info("Terminated {} connections to database {}", terminated, dbName);
                } catch (SQLException e) {
                    log.warn("Could not terminate connections: {}", e.getMessage());
                }

                // 3. Удаляем физическую базу данных
                String quotedDbName = quoteIdent(conn, dbName);
                String dropSql = "DROP DATABASE IF EXISTS " + quotedDbName;
                log.debug("Executing: {}", dropSql);
                stmt.executeUpdate(dropSql);

                // 4. Удаляем запись из метаданных
                String deleteMetadataSql = "DELETE FROM databases_metadata WHERE db_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMetadataSql)) {
                    pstmt.setString(1, dbName);
                    int deleted = pstmt.executeUpdate();
                    log.info("Deleted metadata entry for database {}: {}", dbName, deleted > 0 ? "success" : "not found");
                }

                response.put("success", true);
                response.put("message", "База данных '" + dbName + "' удалена");
                log.info("Database {} deleted successfully", dbName);

            } catch (SQLException e) {
                log.error("Failed to delete database: {}", dbName, e);

                if (e.getMessage().contains("being accessed by other users")) {
                    response.put("error", "Некоторые подключения не удалось завершить. Попробуйте перезапустить контейнер PostgreSQL или подождать несколько минут.");
                } else {
                    response.put("error", "Ошибка удаления базы данных: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error while deleting database: {}", dbName, e);
            response.put("error", "Неожиданная ошибка: " + e.getMessage());
        }
    }
}