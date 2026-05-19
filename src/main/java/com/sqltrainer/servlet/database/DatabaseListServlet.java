package com.sqltrainer.servlet.database;

import com.sqltrainer.config.DatabaseConfig;
import com.sqltrainer.util.QueryExecutor;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Сервлет для получения списка доступных учебных баз данных.
 * Возвращает структурированный список папок с базами данных.
 * Учитывает видимость базы и период доступа.
 * Также возвращает max_rows для каждой базы.
 */
@WebServlet("/api/databases")
public class DatabaseListServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DatabaseListServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> response = new HashMap<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.TEACHER, "postgres")) {
            List<Map<String, Object>> folders = getFoldersWithDatabases(conn);
            response.put("success", true);
            response.put("folders", folders);
            response.put("count", folders.stream().mapToInt(f -> ((List<?>) f.get("databases")).size()).sum());
        } catch (SQLException e) {
            log.error("Failed to list databases: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Не удалось загрузить список баз данных: " + e.getMessage());
            QueryExecutor.clearCache();
        }

        resp.getWriter().write(gson.toJson(response));
    }

    /**
     * Возвращает список папок с базами данных, доступными студентам.
     * Учитывает видимость базы и период доступа.
     * Включает поле max_rows для каждой базы.
     */
    private List<Map<String, Object>> getFoldersWithDatabases(Connection conn) throws SQLException {
        List<Map<String, Object>> folders = new ArrayList<>();

        String sql =
                "SELECT " +
                        "   df.id as folder_id, " +
                        "   df.name as folder_name, " +
                        "   dm.db_name, " +
                        "   dm.display_name, " +
                        "   dm.access_password_hash, " +
                        "   dm.schema_image_url, " +
                        "   dm.max_rows " +
                        "FROM database_folders df " +
                        "LEFT JOIN databases_metadata dm ON dm.folder_id = df.id " +
                        "WHERE dm.is_visible = true " +
                        "  AND (dm.access_start IS NULL OR dm.access_start <= CURRENT_DATE) " +
                        "  AND (dm.access_end IS NULL OR dm.access_end >= CURRENT_DATE) " +
                        "ORDER BY df.name, dm.display_name";

        Map<Long, Map<String, Object>> folderMap = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                long folderId = rs.getLong("folder_id");
                String folderName = rs.getString("folder_name");

                Map<String, Object> folder = folderMap.get(folderId);
                if (folder == null) {
                    folder = new LinkedHashMap<>();
                    folder.put("id", folderId);
                    folder.put("name", folderName);
                    folder.put("databases", new ArrayList<Map<String, Object>>());
                    folderMap.put(folderId, folder);
                }

                String dbName = rs.getString("db_name");
                if (dbName != null && !dbName.isEmpty()) {
                    Map<String, Object> database = new LinkedHashMap<>();
                    database.put("dbName", dbName);
                    database.put("displayName", rs.getString("display_name"));
                    database.put("hasPassword", rs.getString("access_password_hash") != null);
                    database.put("schemaImageUrl", rs.getString("schema_image_url"));
                    database.put("maxRows", rs.getInt("max_rows"));

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> dbList = (List<Map<String, Object>>) folder.get("databases");
                    dbList.add(database);
                }
            }
        }

        folders.addAll(folderMap.values());
        return folders;
    }
}