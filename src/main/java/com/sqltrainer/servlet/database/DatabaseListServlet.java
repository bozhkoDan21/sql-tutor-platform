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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Сервлет для получения списка доступных учебных баз данных.
 * Исключает системные базы PostgreSQL (template0, template1, postgres).
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
        List<String> databases = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection(DatabaseConfig.Role.STUDENT, "postgres")) {
            String sql = "SELECT datname FROM pg_database " +
                    "WHERE datistemplate = false " +
                    "AND datname NOT IN ('postgres', 'template0', 'template1') " +
                    "AND datname NOT LIKE 'template%' " +
                    "ORDER BY datname";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    databases.add(rs.getString("datname"));
                }
            }

            response.put("success", true);
            response.put("databases", databases);
            response.put("count", databases.size());

            log.debug("Listed {} databases for student", databases.size());

        } catch (SQLException e) {
            log.error("Failed to list databases: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Failed to load databases: " + e.getMessage());

            // При ошибке подключения очищаем кеш
            QueryExecutor.clearCache();
        }

        resp.getWriter().write(gson.toJson(response));
    }
}